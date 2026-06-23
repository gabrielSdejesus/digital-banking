# Especificação Técnica: Endpoint de Criação de Conta (`POST /v1/accounts/create`)
            
Este documento detalha a especificação absoluta de componentes, responsabilidades e fluxos de validação para o caso de uso de Criação de Conta.

## 1. Visão Geral do Endpoint

* **Rota:** `/v1/accounts/create`
* **Método HTTP:** `POST`
* **Content-Type Entrada/Saída:** `application/json`
* **Objetivo:** Provisionar uma nova conta bancária válida no sistema, gerando um identificador imutável (UUID) e estabelecendo o saldo inicial de forma blindada contra falhas de dados.

---

## 2. Mapa de Componentes (Onde criar cada arquivo)

Os componentes deste endpoint devem ser distribuídos estritamente nos seguintes pacotes:

```text
com.digitalbanking
│
├── domain/
│   └── account/
│       ├── Account (Entidade de Domínio)
│       └── AccountRepository (Porta de Saída - Interface)
│
├── application/
│   └── account/
│       ├── CreateAccountUseCase (Caso de Uso / Serviço de Aplicação)
│       ├── CreateAccountRequest (DTO de Entrada - Record)
│       └── dto/
│           └── AccountResponse (DTO de Saída - Record)
│       └── mapper/
│           └── AccountMapper (Conversor DTO - Domínio)
│
├── infra/
│   └── database/
│       └── account/
│           ├── AccountDbEntity (Entidade de Banco de Dados / Spring Data)
│           ├── SpringDataAccountRepository (Interface do Spring Data JDBC)
│           └── AccountRepositoryImpl (Implementação da Porta do Domínio)
│
└── interfaces/
    └── rest/
        └── account/
            └── AccountController (Controller REST)
```

---

## 3. Especificação Detalhada por Camada

### 🔵 Camada de Interfaces (Porta de Entrada)
Responsável por receber a requisição de rede, tratar a serialização e gerenciar o protocolo HTTP.

#### `AccountController`
* **Responsabilidade:** Capturar o corpo da requisição JSON, delegar a execução diretamente para o Caso de Uso da camada de aplicação e retornar o status HTTP `201 Created` com o payload de saída.
* **Dependência:** Conhece apenas o `CreateAccountUseCase` e os DTOs. Não possui qualquer acoplamento com o banco de dados ou com a entidade de domínio diretamente.

### 🟡 Camada de Aplicação (Orquestração e DTOs)
Responsável por coordenar o fluxo de execução, garantir os limites transacionais e fazer a ponte entre o mundo externo e o domínio.

#### `CreateAccountRequest` (DTO de Entrada)
* **Tipo:** Java `Record` (imutável).
* **Campos:** `holderName` (String) e `initialBalance` (BigDecimal).
* **Validação Sintática (Programática):** Executada obrigatoriamente dentro do construtor compacto do Record. Se qualquer validação falhar, lança imediatamente uma exceção customizada do tipo `InvalidArgumentException`.
  * *Regra para `holderName`:* Não pode ser nulo, não pode estar em branco, tamanho mínimo de 3 caracteres e máximo de 100 caracteres.
  * *Regra para `initialBalance` (Mínimo):* Não pode ser nulo e deve ser maior ou igual a zero (`0.0000`).
  * *Regra para `initialBalance` (Máximo):* Deve ser menor ou igual a `"99999999999999.9999"` (Teto físico suportado pela estrutura `DECIMAL(18,4)` do banco de dados). Isso impede alocação abusiva de memória pelo parser e garante a consistência técnica da aplicação antes de tocar as camadas internas.
  * *Regra para `initialBalance` (Precisão):* Não pode conter mais de 4 casas decimais. Qualquer valor com maior precisão decimal (por exemplo, `100.12345`) será rejeitado lançando `InvalidArgumentException` (BANK-001).

#### `CreateAccountUseCase` (Serviço de Aplicação)
* **Responsabilidade:**
    1. Receber os dados já validados sintaticamente pelo DTO.
    2. Gerar de forma segura o ID da conta através de um UUID Versão 4 convertido para String.
    3. Instanciar a Entidade de Domínio `Account`.
    4. Invocar a Porta de Persistência (`AccountRepository`) para salvar a nova conta.
    5. Converter a entidade resultante no DTO de saída `AccountResponse` e retorná-lo.
* **Transacionalidade:** Este método deve ser anotado com `@Transactional` do Spring para garantir que a inserção ocorra como uma operação atômica.

#### `AccountResponse` (DTO de Saída)
* **Campos retornados:** `id` (String UUID), `holderName` (String), `balance` (BigDecimal) e `createdAt` (Timestamp ISO-8601 em UTC, ex: `2026-06-22T20:34:36.000Z`).
* **Tratamento de Timezone:** A API opera inteiramente em UTC. O timestamp `createdAt` é gerado e retornado como um `Instant` puro do Java, sem offsets locais ou cabeçalhos de fuso horário.

### 🟣 Camada de Domínio (O Coração do Negócio)
Responsável por proteger as regras de negócio mais sagradas do banco digital. É 100% isolada de frameworks.

#### `Account` (Entidade de Domínio)
* **Atributos:** `id` (UUID), `holderName` (String), `balance` (BigDecimal).
* **Validação Semântica (Regras de Negócio):** Executada no momento da construção do objeto de domínio.
* **Garantia de Estado Válido:** O construtor do domínio valida se o saldo não viola as regras e esta validação bloqueará a criação lançando uma `BusinessRuleException`.
* **Encapsulamento:** Não possui métodos modificadores comuns (setters). O saldo e o nome só podem ser lidos.

#### `AccountRepository` (Porta do Domínio)
* **Tipo:** Interface pura.
* **Responsabilidade:** Declarar o contrato de salvamento (`save(Account account)`), utilizando apenas objetos do domínio.

### 🟢 Camada de Infraestrutura (Detalhes Técnicos e Banco)
Responsável por implementar os contratos e conversar com o banco de dados H2.

#### Estrutura Física (Tabela `account`)
Para que a conta seja considerada válida e persistida com segurança, a tabela deve refletir exatamente os tipos de alta precisão financeira:
* **`id`:** `VARCHAR(36)` como Chave Primária.
* **`holder_name`:** `VARCHAR(100) NOT NULL`.
* **`balance`:** `DECIMAL(18, 4) NOT NULL` (Garante a precisão de 4 casas decimais exigida por grandes bancos).
* **`created_at`:** Data de criação da conta.

#### Componentes de Código na Infra
* **`AccountDbEntity`:** Classe simples anotada para o Spring Data JDBC que espelha as colunas da tabela `account`.
* **`SpringDataAccountRepository`:** Interface que herda os comportamentos nativos do Spring Data (como `CrudRepository`).
* **`AccountRepositoryImpl`:** Classe que implementa a interface `AccountRepository` do Domínio. Ela recebe a entidade de domínio, utiliza um Mapper para convertê-la em `AccountDbEntity`, chama o `SpringDataAccountRepository` para salvar no banco físico e converte o resultado de volta para o Domínio.

---

## 4. Fluxo de Execução Passo a Passo (Cenário de Sucesso)

```text
[Cliente HTTP] 
        │ (Envia JSON)
        ▼
[AccountController]
        │ 
        ▼ Instancia ──▶ [CreateAccountRequest] (Valida tamanho e nulos no construtor)
        │                                       * Se falhar: lança InvalidArgumentException
        ▼ Dados Limpos
[CreateAccountUseCase]
        │ 
        ├─▶ Gera UUID v4 String
        ├─▶ Instancia ──▶ [Account (Domínio)] (Valida regras de negócio do saldo)
        │                                     * Se falhar: lança BusinessRuleException
        ▼
[AccountRepositoryImpl]
        │ Mapeia Domínio para DbEntity
        ▼
[Banco de Dados H2] (Persiste na tabela account com DECIMAL(18,4))
```