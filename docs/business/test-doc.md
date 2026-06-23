# Documentação de Cenários de Testes (`test-doc.md`)

Este documento detalha todos os cenários de testes implementados no projeto **digital-banking**, cobrindo testes unitários, testes de integração e testes de concorrência distribuídos pelas camadas da arquitetura DDD.

---

## 1. Camada de Interfaces (Presentation Layer)

Esta camada valida a serialização/deserialização JSON, os contratos REST, os códigos de status HTTP e o tratamento global de erros da API.

### 1.1. `AccountControllerTest`
Focado nas validações sintáticas e de limites físicos na criação de contas.
* **`shouldReturnCreatedWhenValidRequest`:** Garante que dados de entrada válidos criem uma conta com sucesso retornando status `201 Created` e a estrutura do payload correta.
* **`shouldReturnBadRequestWhenHolderNameIsShort`:** Valida o limite de tamanho mínimo do nome do titular (mínimo 3 caracteres), retornando `400 Bad Request`.
* **`shouldReturnBadRequestWhenBalanceIsNegative`:** Garante a rejeição de saldos iniciais negativos, retornando `400 Bad Request`.
* **`shouldReturnBadRequestWhenBalanceExceedsMaximumLimit`:** Impede estouros de precisão física do banco (`DECIMAL(18,4)`) rejeitando valores acima de `99999999999999.9999`, retornando `400 Bad Request`.
* **`shouldReturnBadRequestWhenBalanceHasTooManyDecimals`:** Rejeita valores com mais de 4 casas decimais na criação de conta, retornando `400 Bad Request`.

### 1.2. `TransactionControllerTest`
Valida o envio de requisições de transferências e as rotas de extratos.
* **`shouldReturnOkWhenPostTransferIsValid`:** Valida o fluxo de sucesso de transferências de dinheiro entre contas, retornando status `200 OK` (incluindo a serialização correta de timestamps em formato ISO-8601 UTC).
* **`shouldReturnOkForStatementRangeRequest`:** Garante que a consulta de extratos por período retorne o envelope de paginação `PagedTransferResponse` contendo os dados e os metadados de paginação (nextCursor, hasMore).
* **`shouldReturnPdfForStatementYearRequest`:** Garante a geração e download correto do PDF de extrato anual em binário (`application/pdf`) com os headers corretos de anexo.
* **`shouldReturnBadRequestWhenTransferAmountHasTooManyDecimals`:** Rejeita tentativas de transferências com precisão fracionária excessiva (> 4 casas decimais).
* **`shouldReturnJsonErrorWhenPdfStatementFails`:** Garante que falhas na geração do PDF de extrato (por exemplo, erros de validação de tamanho de conta) retornem a resposta formatada JSON de erro com status `400 Bad Request`.
* **`shouldReturnBadRequestWhenIdempotencyKeyIsMissing`:** Garante a rejeição automática com `400 Bad Request` quando a requisição de transferência omitir a chave de idempotência obrigatória.
* **`shouldReturnBadRequestWhenIdempotencyKeyIsMalformed`:** Garante a rejeição com `400 Bad Request` caso a chave enviada no header esteja malformatada.

### 1.3. `GlobalExceptionHandlerTest`
Verifica a tradução centralizada de exceções internas em respostas JSON com códigos de erro de negócios padronizados (BANK-xxx).
* **`shouldSuccessfullyHandleInvalidArgumentException`:** Traduz erros sintáticos para `400 Bad Request` com o código `BANK-001`.
* **`shouldSuccessfullyHandleMethodArgumentNotValidException`:** Captura validações automáticas do Spring Boot nos payloads e converte para `400 Bad Request`.
* **`shouldSuccessfullyHandleEntityNotFoundException`:** Traduz a ausência de entidades físicas no banco (como contas inexistentes) para `404 Not Found` com código `BANK-003`.
* **`shouldSuccessfullyHandleBusinessRuleException`:** Traduz falhas nas regras de negócio de domínio (como saldo insuficiente) para `422 Unprocessable Entity` com código `BANK-002`.
* **`shouldSuccessfullyHandleQueryTimeoutException`:** Traduz timeouts de lock de concorrência excessiva no banco para `423 Locked` com código `BANK-004`.
* **`shouldSuccessfullyHandlegenericException`:** Garante que qualquer erro não mapeado no sistema seja encapsulado sob um erro genérico `500 Internal Server Error` (código `BANK-999`) sem expor detalhes sensíveis de stack trace para o cliente externo.
* **`shouldSuccessfullyHandleMethodArgumentTypeMismatchException`:** Trata erros de conversão de parâmetros de URL e query (ex: passar uma string onde se espera um inteiro), devolvendo `400 Bad Request`.
* **`shouldSuccessfullyHandleHttpRequestMethodNotSupportedException`:** Captura chamadas a verbos HTTP não mapeados (como dar POST em rotas GET) e devolve `405 Method Not Allowed` com código `BANK-005`.

---

## 2. Camada de Aplicação (Application Layer)

Valida as regras de orquestração de negócios, os casos de uso do sistema, e o mapeamento correto entre objetos.

### 2.1. `CreateAccountUseCaseTest`
* **`shouldCreateAccountSuccessfully`:** Executa o caso de uso e valida se os IDs são gerados em formato UUID e se o salvamento no repositório de domínio é invocado corretamente.
* **`shouldThrowInvalidArgumentExceptionWhenInitialBalanceExceedsMaximumLimit`:** Garante que o caso de uso bloqueie requisições de saldos que violam a barreira máxima numérica do DTO.
* **`shouldThrowBusinessRuleExceptionWhenDomainAccountBalanceExceedsMaximumLimit`:** Garante o bloqueio de saldos excessivos também a nível de construtor do domínio (defesa em profundidade).

### 2.2. `TransferUseCaseTest`
* **`shouldSuccessfullyExecuteTransferAndEnforceLockOrdering`:** Testa a ordenação alfanumérica de locks (menor ID primeiro) para mitigar deadlocks em execuções paralelas.
* **`shouldThrowBusinessRuleExceptionWhenSourceBalanceIsInsufficient`:** Garante que a transferência seja abortada e lance erro caso a conta de origem não possua saldo disponível suficiente para cobrir o valor.
* **`shouldThrowInvalidArgumentExceptionWhenSourceAndDestinationAreTheSameAccount`:** Impede que um usuário faça transferências de uma conta para ela mesma, lançando erro de argumento inválido.
* **`shouldThrowExceptionWhenIdempotencyKeyIsEmpty`:** Garante que tentativas de envio com a chave de idempotência nula ou em branco sejam bloqueadas com erro de argumento inválido.
* **`shouldThrowExceptionWhenIdempotencyKeyIsMalformed`:** Garante a rejeição de chaves de idempotência que não seguem o formato de UUID v4.
* **`shouldReturnCachedResponseForRepeatIdempotentRequest`:** Garante que uma requisição idêntica e repetida com a mesma chave retorne os dados gravados em cache sem executar novas transações de domínio ou de persistência.
* **`shouldThrowExceptionWhenDailyLimitIsExceeded`:** Valida que a transferência falhe com `BusinessRuleException` se a soma de transferências feitas no dia calendário atual (UTC) ultrapassar o limite de R$ 5.000,00.
* **`shouldThrowExceptionWhenIdempotencyKeyIsReusedWithDifferentParameters`:** Garante que requisições repetidas com a mesma chave de idempotência, porém com parâmetros divergentes (origem, destino ou valor alterados), sejam bloqueadas imediatamente com `InvalidArgumentException` para evitar fraude, colisões de chaves e vazamento de dados de outras transações.

### 2.3. `GetStatementUseCaseTest`
* **`shouldSuccessfullyRetrieveStatementsWhenDateRangeIsWithin 90 days`:** Garante a consulta de extratos quando o intervalo está dentro do limite operacional de 90 dias, retornando o formato envelopado com metadados de paginação.
* **`shouldReturnNextCursorWhenHasMore`:** Garante a quebra de página (hasMore=true) e a geração correta do cursor Base64 contendo a data/hora e o ID da transação limite.
* **`shouldParseAndPassCursorToRepository`:** Valida o parse e decodificação corretos do cursor Base64 enviado, encaminhando os dados desmembrados para a camada de persistência.
* **`shouldClampPageSizeLimits`:** Garante que limites de tamanho de página inválidos (menor/igual a zero ou maior que 100) sejam limpos/ajustados automaticamente (padrão 20, máximo 100).
* **`shouldThrowExceptionWhenCursorIsMalformed`:** Garante a rejeição e lançamento de exceção `InvalidArgumentException` caso o cursor fornecido não seja Base64 válido ou não respeite o padrão `Instant,UUID`.
* **`shouldThrowInvalidArgumentExceptionWhenDateRangeExceeds90DaysThreshold`:** Bloqueia consultas que tentam resgatar períodos superiores a 90 dias para economizar recursos de hardware.
* **`shouldThrowEntityNotFoundExceptionWhenStatementAccountDoesNotExist`:** Lança erro se a conta informada na URL não existir na base de dados.
* **`shouldThrowInvalidArgumentExceptionWhenStartDateFormatIsMalformed`:** Rejeita strings de datas fora do padrão ISO-8601.

### 2.4. `GeneratePdfStatementUseCaseTest`
* **`shouldSuccessfullyGenerateStatementPDFWhenParametersAreValid`:** Valida a chamada de extração de relatórios anuais.
* **`shouldThrowInvalidArgumentExceptionWhenRequestedYearLiesInTheFuture`:** Impede consultas de relatórios de anos futuros.
* **`shouldThrowInvalidArgumentExceptionWhenRequestedYearIsPriorTo1970`:** Impede consultas de relatórios de anos anteriores ao início da época Unix (1970).

### 2.5. `AccountMapperTest`
* **`shouldMapCreateAccountRequestToDomain`:** Valida o mapeamento de entrada.
* **`shouldMapDomainToAccountResponse`:** Valida o mapeamento de saída.

---

## 3. Camada de Infraestrutura e Resiliência (Infrastructure Layer)

Testes de persistência em banco H2 físico, orquestradores de processamento assíncrono e testes de carga sob corrida concorrente.

### 3.1. `AccountRepositoryImplIT` (Integração H2)
* **`shouldSuccessfullySaveAndRetrieveAccountFromH2Database`:** Garante a compatibilidade e persistência física das entidades do domínio mapeadas nas tabelas relacionais do H2.

### 3.2. `TransactionRepositoryImplTest`
* **`shouldRetrieveAccountBalanceUsingPessimisticLockSuccessfully`:** Valida a execução de queries com lock pessimista (`SELECT FOR UPDATE`) para controle transacional do saldo.
* **`shouldSuccessfullyUpdateAccountBalanceBySettingIsNewFlagToFalse`:** Garante que o Spring Data JDBC atualize a linha em vez de criar uma nova entidade duplicada.
* **`shouldRetrieveAndMapPendingOutboxEventsSuccessfully`:** Garante a integridade de leitura de eventos no formato JSON para o Worker.
* **`shouldUpdateOutboxStatusSuccessfullyWhenEventIsFound`:** Valida a atualização de estados dos eventos no Outbox.

### 3.3. `OutboxProcessorTest`
* **`shouldSkipProcessingWhenNoPendingOutboxEventsAreFound`:** Evita processamento desnecessário quando a fila está limpa.
* **`shouldSuccessfullyProcessPendingOutboxEventsAndMarkThemAsPROCESSED`:** Garante a entrega resiliente (*At-Least-Once*) atualizando para `PROCESSED` após o envio da notificação simulada.
* **`shouldSkipEventWhenClaimFails`:** Garante que o worker pule um evento caso outra instância concorrente do microsserviço já tenha conseguido realizar o claim (lock via CAS do banco de dados).
* **`shouldMarkEventAsFailedOnException`:** Valida que o processador incremente o contador de tentativas e reverta para PENDING ou mova para DLQ em caso de falha de comunicação/rede no disparo da notificação.

### 3.4. `TransferConcurrencyIT` (Teste de Alta Concorrência)
* **`shouldProcessConcurrentTransfersCorrectlyWithoutDoubleSpendingUnderHeavyRaceConditions`:** O teste mais importante de consistência. Ele cria múltiplas threads paralelas que executam transferências cruzadas simultâneas. Garante que, independentemente da quantidade de threads solicitando transferências de forma concorrente no mesmo milissegundo, a integridade financeira do saldo seja perfeitamente preservada e nenhum centavo seja duplicado ou sumido do banco.
* **`shouldTimeoutWhenLockIsHeld`:** Simula um timeout de lock pessimista onde uma transação tenta adquirir o lock na mesma conta que já está bloqueada por outra operação demorada. Garante que a transação falhe rápido (após 2 segundos configurados) e propague a exceção `QueryTimeoutException`, que é mapeada para HTTP `423 Locked`.