## Padrões de Design e Documentação de API (Swagger/OpenAPI)

Todos os endpoints devem ser projetados e documentados seguindo estritamente a **OpenAPI Specification (OAS 3.0)** e inspirados no **Microsoft API Design Guidelines**. O sistema deve buscar o **Nível 3 do Modelo de Maturidade de Richardson**, utilizando códigos de status HTTP semânticos, roteamento correto orientado a recursos e respostas explícitas de metadados.

### 1. Requisitos do Springdoc OpenAPI:
- **Sem Documentação Anêmica:** Cada controller REST e seus respectivos endpoints devem ser obrigatoriamente anotados com `@Operation`, fornecendo um resumo (`summary`) e uma descrição (`description`) claros do comportamento de negócio.
- **Mapeamento de Restrições de Propriedades:** Todas as propriedades dos esquemas de DTOs de entrada (Request) devem declarar explicitamente seus limites técnicos usando anotações `@Schema` (ex: `minimum`, `maximum`, `minLength`, `maxLength`, `example` e `requiredMode`). Esses limites devem espelhar exatamente as validações impostas nos construtores dos `Records` Java.
- **Matriz de Múltiplas Respostas Explícita:** Não documente apenas o fluxo de sucesso. Cada endpoint deve declarar explicitamente sua matriz completa de respostas de erro através de `@ApiResponses`.

### 2. Esquema Padrão de Conteúdo de Resposta:
- **HTTP 200/201 (Sucesso):** Deve mapear o esquema do Record de DTO de saída correspondente.
- **HTTP 400 (Bad Request):** Deve mapear o esquema `ApiErrorResponse`, ilustrando explicitamente violações de restrições estruturais, sintáticas ou JSON malformado.
- **HTTP 404 (Not Found):** Deve mapear o esquema `ApiErrorResponse` quando o recurso/conta solicitado por UUID não for encontrado na base.
- **HTTP 405 (Method Not Allowed):** Deve mapear o esquema `ApiErrorResponse` para verbos HTTP incorretos na rota.
- **HTTP 422 (Unprocessable Entity):** Deve mapear o esquema `ApiErrorResponse` para capturar falhas semânticas de regras de negócio (ex: saldo insuficiente, falhas de validação de intervalo de datas).
- **HTTP 423 (Locked):** Deve mapear o esquema `ApiErrorResponse` indicando que a requisição falhou rápido devido ao estouro de timeout na disputa pelo Lock Pessimista do banco.