# Especificação Técnica: Processamento de Outbox (`outbox-spec.md`)

Este documento detalha o funcionamento do worker de processamento do Transactional Outbox, responsável por consumir de forma resiliente as notificações pendentes e exibi-las no console (simulando a entrega a um message broker ou serviço externo).

---

## 1. Visão Geral do Worker

O processamento do outbox adota a estratégia de polling periódico no banco de dados para buscar novos eventos gerados durante as transações financeiras.

* **Frequência:** Execução periódica a cada 5 segundos (configurável).
* **Escopo:** Buscar registros com `status = 'PENDING'`, processar cada um individualmente, e atualizar o status final para `PROCESSED` ou `FAILED`.
* **Garantia de Entrega:** *At-Least-Once* (Pelo menos uma vez), garantindo que nenhuma notificação seja perdida.

---

## 2. Mapa de Componentes e Estrutura DDD (Extensões)

```text
com.corebank.digital_banking
│
├── domain/
│   └── transaction/
│       ├── OutboxEvent (Representa o evento)
│       └── TransactionRepository (Adicionar métodos findPendingOutboxEvents e updateOutboxStatus)
│
├── application/
│   └── transaction/
│       └── OutboxProcessor (Componente scheduler em background / Worker de processamento)
│
└── infra/
    └── database/
        └── transaction/
            ├── SpringDataOutboxRepository (Adicionar query para busca por status)
            └── TransactionRepositoryImpl (Implementação dos novos métodos)
```

---

## 3. Estratégia de Resiliência e Concorrência

### 3.1 Transição de Estados (Status Transition)
O processador de outbox utiliza um fluxo de transição de estado atômico para evitar processamentos concorrentes entre múltiplas instâncias da API e gerenciar falhas de forma automática:
```text
[PENDING] ──(Claim por Worker)──▶ [PROCESSING] ──(Sucesso no envio)──▶ [PROCESSED]
    ▲                                 │
    │                                 ├──(Falha de Envio & retry_count < 3)
    └─────────────────────────────────┘
                                      │
                                      └──(Falha de Envio & retry_count >= 3)──▶ [DLQ]
```

* **Mecanismo de Claim (Lock Distribuído por Banco):** Antes de iniciar o processamento de um evento, o worker tenta transitar atomicamente seu status de `PENDING` para `PROCESSING` filtrando por ID. Somente a instância que obtiver êxito (1 linha afetada no update) executa o processamento do evento.
* **Tratamento de Exceções e DLQ (Dead Letter Queue):** Caso ocorra uma falha inesperada durante a simulação do envio, o worker captura o erro, incrementa o contador `retry_count` do evento no banco e o reverte para `PENDING` para que possa ser coletado novamente. Se o limite de **3 tentativas** for atingido, o evento é marcado como `DLQ` e removido da fila operacional de processamento.

### 3.2 Estrutura Física (Tabela `notification_outbox`)
Para gerenciar a concorrência e as retentativas das mensagens, a tabela `notification_outbox` é especificada com as seguintes colunas:
* **`id`:** `BIGINT AUTO_INCREMENT` como Chave Primária.
* **`transfer_id`:** `VARCHAR(36) NOT NULL` (Chave Estrangeira referenciando a tabela `transfer`).
* **`payload`:** `VARCHAR(1000) NOT NULL` (Payload JSON contendo a mensagem de notificação).
* **`status`:** `VARCHAR(20) NOT NULL` (Estado operacional da fila: `PENDING`, `PROCESSING`, `PROCESSED`, `DLQ`).
* **`retry_count`:** `INT DEFAULT 0 NOT NULL` (Contador incremental de falhas de processamento).
* **`created_at`:** `TIMESTAMP NOT NULL` (Data de geração do evento em UTC).

---

## 4. Fluxo de Execução Passo a Passo (Processamento de Sucesso)

```text
[OutboxProcessor (Scheduler)]
       │
       ▼ (Dispara a cada 5s)
[TransactionRepositoryImpl] ──▶ Busca eventos com status 'PENDING'
       │
       ▼ (Retorna lista de eventos pendentes)
Para cada OutboxEvent:
       │
       ├─▶ Printa no console a mensagem de notificação formatada
       │   Exemplo: "[NOTIFICATION WORKER] Evento do outbox {id} processado com sucesso. Payload: {payload}"
       │
       ├─▶ Atualiza status do evento para 'PROCESSED'
       │
       ▼ (Salva alterações no Banco de Dados H2)
[H2 Database] ──▶ Commit da transação de processamento do evento
```
