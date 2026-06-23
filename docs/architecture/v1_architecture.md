# Guia de Arquitetura de Referência: Estrutura de Pastas DDD em Java
            
Este documento define a estrutura padrão, o fluxo de dependências e as responsabilidades das camadas para o desenvolvimento de projetos baseados em **Domain-Driven Design (DDD)** e **Arquitetura em Camadas** utilizando o ecossistema Java/Spring.

---

## 1. Visão Geral e Fluxo de Dependências

A arquitetura adota o princípio de inversão de dependência e isolamento do domínio Core. 
* **Regra de Ouro:** As dependências sempre apontam para **dentro**. Camadas externas conhecem as internas, mas as internas nunca conhecem os detalhes de implementação das externas.

```
[ INTERFACES (API/Entrada) ] ──> [ INFRASTRUCTURE ] ──> [ APPLICATION (Casos de Uso) ] ──> [ DOMAIN (Regras de Negócio) ]
```

---

## 2. Estrutura de Diretórios e Pacotes

A árvore de arquivos padrão do projeto deve seguir estritamente o modelo abaixo:

```text
meu-projeto-enterprise/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com.meuprojeto/
│   │   │       ├── domain/            # 1. Camada de Domínio (Coração da aplicação)
│   │   │       │   ├── cliente/
│   │   │       │   ├── pagamento/
│   │   │       │   └── compartilhado/
│   │   │       ├── application/       # 2. Camada de Aplicação (Casos de uso/Orquestração)
│   │   │       │   ├── port/
│   │   │       │   ├── cliente/
│   │   │       │   └── pagamento/  
│   │   │       │       ├── dto/
│   │   │       │       ├── mapper/
│   │   │       ├── infra/             # 3. Camada de Infraestrutura (Tecnologia/Adaptadores)
│   │   │       │   ├── config/
│   │   │       │   ├── database/
│   │   │       │   ├── repository/
│   │   │       │   ├── messaging/
│   │   │       │   ├── webclient/
│   │   │       │   └── security/
│   │   │       └── interfaces/        # 4. Camada de Interfaces (Pontas de entrada da API)
│   │   │           ├── rest/
│   │   │           ├── exception/
│   │   │           └── advice/
│   │   └── resources/                 # Configurações globais da aplicação
│   │       ├── application.yml
│   │       ├── logback-spring.xml
│   └── test/                          # Testes Unitários e de Integração
│       ├── java/
│       └── resources/
│   │       └── db/                    # Scripts Banco de Dados
├── pom.xml / build.gradle             # Dependências e Build do Projeto
└── README.md                          # Visão geral do repositório
```

---

## 3. Detalhamento e Responsabilidades de Cada Camada

As classes, posicione-as de acordo com as seguintes definições de escopo:

### 3.1. DOMAIN (Domínio)
É o núcleo da aplicação. Contém puramente as regras de negócio e o modelo conceitual do domínio.

* **Entidades:** Classes que possuem uma identidade única e mantêm o estado do negócio.
* **Value Objects:** Objetos imutáveis definidos por seus atributos (ex: Endereco, Cpf).
* **Agregados (Aggregates):** Grupos de entidades que são tratadas como uma única unidade para mudança de dados.
* **Serviços de Domínio:** Lógicas de negócio puras que envolvem múltiplas entidades e não pertencem logicamente a uma entidade específica.
* **Eventos de Domínio:** Notificações de acontecimentos relevantes para o negócio dentro do domínio.
* **Interfaces de Repositório (Ports):** Contratos abstratos de persistência de dados que a Infraestrutura deverá estender.

### 3.2. APPLICATION (Aplicação)
Responsável por orquestrar os casos de uso do sistema. Ela não executa lógica de negócio diretamente, mas sabe *como coordenar* o domínio e chamar as portas de saída.
* **Casos de Uso / Serviços de Aplicação:** Fluxos de execução coordenando entidades de domínio e chamadas externas.
* **DTOs (Data Transfer Objects):** Objetos de transporte de dados limpos que trafegam entre a camada de Interface e Aplicação.
* **Mappers:** Classes responsáveis por converter objetos de Domínio em DTOs e vice-versa.

### 3.3. INFRASTRUCTURE (Infraestrutura)
Onde o projeto se conecta com o mundo real e com as escolhas tecnológicas. Esta camada implementa as interfaces (*ports*) definidas nas camadas de aplicação e domínio.
* **Conexão e Acesso a Dados:** Configurações de bancos relacionais ou NoSQL, incluindo Spring Data JPA repositories, queries nativas.
* **Implementações de Repositórios:** Classes concretas que estendem as interfaces do domínio e interagem com a persistência de fato.
* **Configurações Técnicas e de Segurança:** Beans de configuração do Spring, filtros do Spring Security, OAuth2, etc.

### 3.4. INTERFACES (Interface / Apresentação)
A porta de entrada da aplicação. Expõe os recursos do sistema e gerencia a comunicação externa com clientes/usuários.
* **Controllers (REST):** Endpoints HTTP recebendo requisições, validando o payload de entrada e mapeando para objetos da aplicação.
* **Tratamento de Exceções & Advice / Handlers:** Captura global de erros para devolver respostas padronizadas e códigos de status HTTP corretos (ex: `@RestControllerAdvice`).