# Especificação Técnica: Transferências, Outbox e Extratos (`transaction-spec.md`)

Este documento detalha os critérios, contratos de endpoints, controle de concorrência distribuída e estratégias de resiliência para o fluxo de transferências financeiras e consultas de movimentação.

---

## 1. Visão Geral dos Endpoints

### 1.1 Efetuar Transferência entre Contas
* **Rota:** `/v1/transfers`
* **Método HTTP:** `POST`
* **Status de Sucesso:** `200 OK` (Optado por `200 OK` dado o lock imediato).

### 1.2 Consultar Movimentações (Por Intervalo de Dias com Paginação por Cursor)
* **Rota:** `/v1/accounts/{accountId}/statements/range`
* **Método HTTP:** `GET`
* **Parâmetros Query:** 
  * `startDate` (ISO-8601 String, Obrigatório)
  * `endDate` (ISO-8601 String, Obrigatório)
  * `pageSize` (int, Opcional, Padrão 20, Máximo 100)
  * `cursor` (String Base64 opaca, Opcional)
* **Response Envelope:** Mapeia um payload JSON contendo a lista de transações (`data`) e metadados de paginação (`paging` com `nextCursor` e `hasMore`).

### 1.3 Consultar Movimentações (Por Ano em PDF)
* **Rota:** `/v1/accounts/{accountId}/statements/year`
* **Método HTTP:** `GET`
* **Parâmetros Query:** `year` (int)
* **Response Header:** `Content-Type: application/pdf`

---

## 2. Mapa de Componentes e Estrutura DDD

```text
com.digitalbanking
│
├── domain/
│   └── transaction/
│       ├── AccountBalance (Entidade ou Aggregate Root para controle de saldo)
│       ├── Transfer (Entidade de Auditoria/Histórico)
│       ├── OutboxEvent (Entidade de resiliência de notificações)
│       └── TransactionRepository (Porta de Saída - Contrato de persistência e Locks)
│
├── application/
│   └── transaction/
│       ├── TransferUseCase (Serviço de Aplicação / Caso de Uso)
│       ├── GetStatementUseCase (Caso de Uso de busca de extrato)
│       ├── GeneratePdfStatementUseCase (Caso de Uso para renderização de extrato)
│       ├── dto/
│       │   ├── TransferRequest (DTO Record de Entrada)
│       │   └── TransferResponse (DTO Record de Saída)
│       └── mapper/
│           └── TransactionMapper (Conversor DTO - Domínio)
│
├── infra/
│   └── database/
│       └── transaction/
│           ├── TransferDbEntity (Tabela transfer de Auditoria)
│           ├── OutboxEventDbEntity (Tabela notification_outbox para o Worker)
│           └── TransactionRepositoryImpl (Implementação de infra com Lock Pessimista)
│
└── interfaces/
    └── rest/
        └── transaction/
            └── TransactionController (Controller REST exposto)
```

---

## 3. Blindagem de Concorrência

### 3.1 Prevenção de Deadlock via Ordenação Dinâmica de Resource Locks
Para evitar deadlocks quando a **Conta A** transfere para a **Conta B** concomitantemente com a **Conta B** transferindo para a **Conta A**, o sistema **nunca** deve adquirir os locks com base no papel de "origem" ou "destino".
* **Regra Executiva:** Os UUIDs das duas contas envolvidas devem ser comparados alfanumericamente (`idOrigem.compareTo(idDestino)`) antes da execução da query.
* O sistema deve dar o `SELECT FOR UPDATE` (Lock Pessimista) primeiramente na conta que possuir o **menor ID** e, somente após o retorno do banco, dar o segundo lock na conta de **maior ID**. Isso lineariza a disputa pelo recurso no banco e zera a ocorrência de deadlocks cíclicos.

### 3.2 Resiliência de Notificação: Pattern Transactional Outbox
Para que o disparo da notificação assíncrona.
1. Dentro do mesmo método `@Transactional` da transferência, além de atualizar os saldos e salvar a auditoria, insere-se um registro na tabela `notification_outbox` com o payload da notificação.
2. Se a transação falhar, o evento sofre rollback. Se persistir, o evento está garantido.
3. Um Worker assíncrono em background lê de forma independente a tabela `notification_outbox` e repassa as mensagens para notificação.

### 3.3 Precisão Monetária Absoluta
Todos os campos de valor do sistema devem usar `BigDecimal` inicializados estritamente por String ou constantes seguras. Toda operação aritmética deve forçar explicitamente a escala de **4 casas decimais** (`DECIMAL(18,4)`) utilizando `.setScale(4, RoundingMode.HALF_DOWN)`.

### 3.4 Tratamento de Fuso Horário e Geração de Relatórios
Para evitar inconsistências de fuso horário local nos servidores de aplicação e banco de dados, o fuso horário da aplicação e dados é padronizado integralmente em UTC:
* **Persistência (Domínio e H2):** Todos os timestamps de auditoria e data de criação são mantidos estritamente em UTC puro através do tipo `java.time.Instant`.
* **Cálculos de Janela Temporal (Filtros de Extrato/PDF):** A validação da janela de 90 dias e o cálculo dos limites anuais são feitos em UTC puro (`ZoneOffset.UTC`).
* **Exibição na API (JSON):** Os dados de data de transação são serializados como um `Instant` UTC puro (ex: `2026-06-22T23:34:36Z`).
* **Extrato PDF (Relatório Físico):** 
  - O cabeçalho do documento exibe o ID da conta e o ano consultado.
  - A tabela física do extrato exibe as colunas: `Source Account` (Nome do Titular de Origem), `Destination Account` (Nome do Titular de Destino), `Amount` (Valor) e `Timestamp` (Data/Hora em formato amigável UTC `dd/MM/yyyy HH:mm:ss`). Os IDs de conta (UUIDs) não são expostos na tabela, apenas os nomes de titulares resolvidos.

### 3.5 Estrutura Física (Tabela `transfer`)
Para suportar o controle de auditoria financeira e a blindagem contra reprocessamento duplicado, a tabela `transfer` deve possuir as seguintes colunas de alta precisão e restrição:
* **`id`:** `VARCHAR(36)` como Chave Primária.
* **`source_account_id`:** `VARCHAR(36) NOT NULL` (Chave Estrangeira apontando para a conta de origem).
* **`destination_account_id`:** `VARCHAR(36) NOT NULL` (Chave Estrangeira apontando para a conta de destino).
* **`amount`:** `DECIMAL(18, 4) NOT NULL` (Garante a precisão de 4 casas decimais do saldo).
* **`idempotency_key`:** `VARCHAR(36) UNIQUE NOT NULL` (Garante que nenhuma transação duplicada seja gravada no banco).
* **`created_at`:** `TIMESTAMP NOT NULL` (Armazena a data/hora exata em UTC).
* **Índices de Paginação (Keyset):** Para otimizar a paginação baseada em cursor descritiva temporal, dois índices compostos de alta performance são declarados:
  - `idx_transfer_source_paged` nas colunas `(source_account_id, created_at DESC, id DESC)`
  - `idx_transfer_dest_paged` nas colunas `(destination_account_id, created_at DESC, id DESC)`

---

## 4. Especificação por Caso de Uso

### 4.1 Caso de Uso: Transferência entre Contas (TransferUseCase)

#### Validações Sintáticas Obrigatórias (No Construtor do TransferRequest):
- sourceAccountId e destinationAccountId: Não nulos, válidos no formato UUID String.
- amount (Mínimo): Não nulo, estritamente maior que zero (0.0000).
- amount (Máximo): Deve ser menor ou igual a "99999999999999.9999" (Limite físico do DECIMAL(18,4)) para evitar estouro de memória/buffer do parser de JSON.
- amount (Precisão): Não pode conter mais de 4 casas decimais. Qualquer valor com maior precisão decimal (por exemplo, `10.12345`) será rejeitado.
- Se violado o teto, o mínimo ou a precisão decimal, lança imediatamente InvalidArgumentException (BANK-001).

#### Fluxo de Execução Regido por Regras de Negócio:
1. **Validação de Idempotência:** Busca por transferências já salvas no banco com a mesma `Idempotency-Key` recebida no header. Se encontrar, valida se o `source_account_id`, `destination_account_id` e o `amount` da requisição atual coincidem exatamente com a transação gravada. Se coincidirem, retorna imediatamente a resposta em cache (`200 OK`). Caso contrário (parâmetros divergentes), lança `InvalidArgumentException` (código `BANK-001`, HTTP `400 Bad Request`) para evitar vazamento de dados ou reprocessamento com payload fraudado/corrompido.
2. Inicia `@Transactional(timeout = 5)` (Garante rollback atômico e falha rápido em caso de timeout de lock).
3. Ordena os IDs alfanumericamente.
4. Executa o lock pessimista na primeira conta (menor ID). Se a conta não existir, lança `EntityNotFoundException` (BANK-003).
4. Executa o lock pessimista na segunda conta (maior ID). Se não existir, lança `EntityNotFoundException` (BANK-003).
5. **Validação de Saldo:** Verifica se a conta de origem possui saldo suficiente (`balance.compareTo(amount) >= 0`). Se não tiver, lança `BusinessRuleException` (BANK-002).
6. Deduz o valor da conta origem e adiciona à conta destino aplicando a precisão de 4 casas decimais.
7. Salva a atualização de saldo de ambas as contas.
8. **Auditoria Imutável (Append-Only):** Registra uma linha na tabela de transações contendo: `id` (UUID), `source_account_id`, `destination_account_id`, `amount`, e `created_at` preenchido dinamicamente com o fuso horário via `java.time.Instant.now()` (Salva dia/mês/ano/hora/min/seg em formato UTC).
9. **Geração do Outbox:** Insere o registro correspondente do evento na tabela `notification_outbox` para o processamento assíncrono do microsserviço de notificações.

### 4.2 Caso de Uso: Consultar Movimentações por Intervalo (`GetStatementUseCase`)
* **Validação de Parâmetros de Data:** A camada de aplicação recebe os parâmetros de data como `String` e valida:
  - Não nulos e não vazios (caso contrário, lança `InvalidArgumentException` com erro HTTP 400).
  - Formato válido de data/tempo sob padrão ISO-8601 (caso contrário, lança `InvalidArgumentException` com código `BANK-001`).
* **Validação de Janela temporal:** A camada de aplicação deve converter os Instants de início e fim no fuso horário UTC (`ZoneOffset.UTC`) para `LocalDate` e calcular a diferença de dias utilizando `java.time.ChronoUnit.DAYS.between()`. Se o intervalo for superior a **90 dias**, lança imediatamente `InvalidArgumentException` (BANK-001).
* **Validação de Existência:** Valida se a conta informada existe na base antes de processar os dados.
* **Validação e Tratamento do Cursor (Keyset Pagination):**
  - O parâmetro `pageSize` é limitado: padrão 20, máximo 100.
  - Se o `cursor` for fornecido, a aplicação decodifica o token Base64 obtendo dois valores: `cursorCreatedAt` (Instant) e `cursorId` (UUID). Se a decodificação falhar ou o formato for inválido, lança `InvalidArgumentException`.
  - A query busca `pageSize + 1` registros a partir do cursor no índice ordenado de forma descendente.
  - Se a quantidade de resultados retornar `pageSize + 1`, a aplicação define `hasMore = true`, remove o último registro sobressalente da lista de retorno e gera o token Base64 do `nextCursor` utilizando as informações do último registro restante (`created_at` e `id`).
  - O retorno é encapsulado no record `PagedTransferResponse`.

### 4.3 Caso de Uso: Consultar Movimentações por Ano (`GeneratePdfStatementUseCase`)
* **Validação do Ano:** O parâmetro numérico do ano deve ser validado no intervalo: `ano >= 1970` e `ano <= Ano Vigente Atual`. Se falhar, lança `InvalidArgumentException`.
* **Geração do Artefato:** Recupera todas as transferências daquele ano em formato de lista imutável e repassa para a biblioteca de renderização de PDF (ex: OpenPDF / iText). O controller expõe o fluxo devolvendo o arquivo binário direto na stream HTTP.

---

## 5. Fluxo de Execução Passo a Passo (Caso de Uso de Transferência - Sucesso)

```text
[Cliente HTTP] 
        │ (Envia TransferRequest JSON)
        ▼
[TransactionController]
        │ 
        ▼ Instancia ──▶ [TransferRequest] (Valida UUIDs, valores e limites no construtor)
        │                                 * Se falhar: lança InvalidArgumentException
        ▼ Dados Limpos
[TransferUseCase] (Inicia @Transactional(timeout = 5))
        │
        ├─▶ Ordena IDs das contas alfanumericamente (Previne Deadlock)
        ├─▶ Invoca Lock Pessimista ──▶ [TransactionRepositoryImpl] ──▶ SELECT FOR UPDATE (Menor ID)
        │                                                               * Se não existir: lança EntityNotFoundException
        ├─▶ Invoca Lock Pessimista ──▶ [TransactionRepositoryImpl] ──▶ SELECT FOR UPDATE (Maior ID)
        │                                                               * Se não existir: lança EntityNotFoundException
        ├─▶ Instancia/Valida ──▶ [AccountBalance (Domínio)] (Executa debit e credit, valida saldo suficiente)
        │                                                   * Se falhar: lança BusinessRuleException
        ├─▶ Persiste saldos atualizados
        ├─▶ Instancia & Salva ──▶ [Transfer (Domínio)] (Registro de auditoria)
        ├─▶ Instancia & Salva ──▶ [OutboxEvent (Domínio)] (Evento de notificação no outbox)
        ▼
[TransactionRepositoryImpl]
        │ Mapeia Domínio para Entidades de Banco (TransferDbEntity, OutboxEventDbEntity)
        ▼
[Banco de Dados H2] (Persiste atualizações de saldo, transfer e notification_outbox sob a mesma transação)
```
