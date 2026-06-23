# Global Error Handling and Exception Management (`error-spec.md`)
            
## 1. Padrão de Resposta de Erro (Contrato Unificado)

Todas as respostas de erro da API, sem exceção, devem seguir rigorosamente a mesma estrutura de payload JSON.

### 1.1 Estrutura do Payload de Erro Geral (`404`, `422`, `423`, `500`)

```json
{
    "timestamp": "2026-06-22T13:45:00Z",
    "status": 422,
    "error": "Unprocessable Entity",
    "message": "Source account has insufficient balance to complete the transaction.",
    "code": "BANK-002"
}
```

### 1.2 Estrutura do Payload de Erro de Validação (400)
Quando houver falhas sintáticas ou múltiplos campos inválidos na entrada de dados, o campo `details` deve ser adicionado para mapear cada inconsistência individualmente:

```json
{
    "timestamp": "2026-06-22T13:45:00Z",
    "status": 400,
    "error": "Bad Request",
    "message": "Validation failed for one or more fields.",
    "code": "BANK-001",
    "details": [
    {
        "field": "holderName",
        "message": "The holder name must be between 3 and 100 characters."
    },
    {
        "field": "initialBalance",
        "message": "The initial balance cannot be negative."
    }
    ]
}
```

---

## 2. Mapa de Componentes na Estrutura DDD
Conforme a regra de dependências do projeto, as exceções nascem onde a regra é violada (Domínio ou Aplicação), mas o mapeamento para o protocolo HTTP acontece exclusivamente na camada externa de Interfaces.

```text
com.digitalbanking
│
├── domain/
│   └── exception/
│       └── BusinessRuleException (Erro de Regra de Negócio/Saldo)
│
├── application/
│   └── exception/
│       ├── EntityNotFoundException (Entidade não encontrada no banco)
│       └── InvalidArgumentException (Campos inválidos na entrada do DTO)
│
└── interfaces/
    └── advice/
        ├── GlobalExceptionHandler (O interceptador central @RestControllerAdvice)
        └── error/
            ├── ApiErrorResponse (O DTO que representa o JSON de erro)
            └── ApiErrorDetail (O DTO auxiliar para a lista de 'details')
```

---

## 3. Matriz de Mapeamento: Exceção vs Status HTTP

O componente `GlobalExceptionHandler` interceptará as exceções lançadas pelas camadas internas (da aplicação e do próprio framework) e fará a conversão exata de acordo com a tabela abaixo, garantindo que nenhum erro HTTP de borda resulte em um falso Erro 500:

| Exceção Originária | Camada de Origem | Status HTTP | Código Interno (code) | Justificativa e Comportamento para o Agente |
| :--- | :--- | :--- | :--- | :--- |
| **InvalidArgumentException** | application / interfaces | 400 Bad Request | `BANK-001` | Lançada manualmente pelos construtores dos DTOs/Records. Coleta o nome do campo e a mensagem de erro específica. |
| **HttpMessageNotReadableException** | infra (Spring/Jackson) | 400 Bad Request | `BANK-001` | Intercepta corpos de requisição com sintaxe JSON corrompida, payloads vazios ou tipos incompatíveis que impedem a desserialização. |
| **MethodArgumentTypeMismatchException** | infra (Spring REST) | 400 Bad Request | `BANK-001` | Lançada quando parâmetros de URL ou Query parameters não correspondem ao tipo esperado (ex: passar texto no parâmetro de ano ou ID). |
| **HttpRequestMethodNotSupportedException** | infra (Spring REST) | 405 Method Not Allowed | `BANK-005` | Lançada quando o cliente tenta utilizar um verbo HTTP incorreto para uma rota existente (ex: enviar `GET` para `/v1/transfers`). |
| **EntityNotFoundException** | application | 404 Not Found | `BANK-003` | Lançada quando um UUID de conta consultado ou usado na transferência não existe no banco de dados. |
| **BusinessRuleException** | domain | 422 Unprocessable Entity | `BANK-002` | Lançada pelas Entidades de Domínio quando regras de saldo, auto-transferência ou limites falham. |
| **QueryTimeoutException** *(ou JDBC)* | infra (Database) | 423 Locked | `BANK-004` | Crucial para Concorrência. Lançada se a thread estourar o tempo limite esperando o Lock Pessimista (`SELECT FOR UPDATE`). O sistema falha rápido. |
| **Exception** *(Genérica)* | Qualquer uma | 500 Internal Error | `BANK-999` | Última barreira de segurança. Captura erros inesperados da JVM (ex: `NullPointerException`). A mensagem técnica/stacktrace original **nunca** é exposta ao cliente. |

---

## 4. Diretrizes Técnicas de Implementação
Ao codificar esta especificação, os seguintes padrões arquiteturais devem ser mantidos de forma estrita:

* **1. Abstração de Mensagens Técnicas no Status 500:** Em caso de erro não mapeado (500), o handler deve realizar o log do stack trace completo no console (via loggers como SLF4J com nível `error`), mas o JSON de retorno deve ocultar os detalhes de infraestrutura por motivos de segurança, exibindo apenas a mensagem fixa: `"An unexpected internal error occurred on our servers. Please try again later."`.
* **2. Imutabilidade dos DTOs de Erro:** As classes de representação do payload de erro, como `ApiErrorResponse` e `ApiErrorDetail`, devem ser implementadas utilizando Java **Records**.
* **3. Captura Dinâmica do Timestamp:** O campo `timestamp` deve capturar o momento exato da interceptação do erro normalizado em formato UTC via `java.time.Instant.now()`.
* **4. Isolamento de Infraestrutura:** O componente `GlobalExceptionHandler` é o único local autorizado a lidar com anotações de infraestrutura web como `@ExceptionHandler`, `@RestControllerAdvice` e classes de resposta HTTP do Spring (`ResponseEntity`). Nenhuma classe de exceção contida dentro de `domain` ou `application` pode herdar ou importar pacotes do Spring Framework.