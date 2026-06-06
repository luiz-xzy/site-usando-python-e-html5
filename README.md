# Inova Tech — Inventory Management System

Sistema de gerenciamento de estoque desenvolvido em **Java 17** seguindo arquitetura profissional em camadas com padrões MVC e DAO.

---

## 🏗️ Arquitetura

```
src/main/java/br/com/inovatech/
├── model/          # POJOs e Enums (Product, User, Movement, Role, MovementType)
├── repository/     # Interfaces DAO + implementações JSON (Jackson)
├── service/        # Lógica de negócio (InventoryService, AuthService, ExportService)
├── view/           # Interface CLI (MainCLI)
└── exception/      # Exceções personalizadas
```

---

## 🚀 Como executar

### Pré-requisitos
- JDK 17+
- Maven 3.8+

### Compilar e rodar
```bash
# Compilar
mvn clean package -DskipTests

# Executar
java -jar target/inventory-management-1.0.0.jar
```

### Rodar os testes
```bash
mvn test
```

---

## 🔐 Usuários padrão

| Usuário    | Senha    | Perfil    |
|------------|----------|-----------|
| admin      | admin123 | GERENTE   |
| supervisor | super123 | SUPERVISOR|

> ⚠️ Altere as senhas em ambiente de produção!

---

## 📐 Padrões e boas práticas implementadas

| Requisito | Implementação |
|-----------|--------------|
| **RF01** Cadastro de Produtos | `Optional<Product>` para buscas seguras |
| **RF02** Autenticação | Hash SHA-256 via `java.security.MessageDigest` |
| **RF03** Thread-safety em saídas | `AtomicInteger` + método `synchronized` |
| **RF04** Alertas de Estoque | Padrão **Observer** (`StockAlertObserver`) |
| **RF05** Exportação | `java.nio.file` → `.csv` |
| **RN01** Precisão de preços | `BigDecimal` (sem erros de ponto flutuante) |
| **RN02** Histórico imutável | `Collections.unmodifiableList()` |
| **PRD §8** Auto-save | Bloco `finally` no main loop |

---

## 🗂️ Persistência

Dados salvos em `data/`:
- `data/produtos.json` — catálogo de produtos
- `data/movimentacoes.json` — histórico de movimentações
- `data/usuarios.json` — usuários do sistema

Exportações em `exports/`:
- `exports/produtos_YYYYMMDD_HHmmss.csv`
- `exports/movimentacoes_YYYYMMDD_HHmmss.csv`

---

## 📊 Estrutura JSON (conforme PRD §7)

```json
{
  "products": [
    {
      "id": 101,
      "sku": "IPH-15-PRO",
      "name": "iPhone 15 Pro Max",
      "currentQuantity": 15,
      "minThreshold": 5,
      "active": true
    }
  ]
}
```

---

## 🧪 Cobertura de Testes (JUnit 5)

- Cadastro com SKU duplicado
- Validação de preço (venda < custo)
- Registro de entrada/saída/ajuste
- `EstoqueInsuficienteException` para saldo insuficiente
- Alerta de estoque baixo via Observer
- **Thread-safety**: 20 threads concorrentes sem saldo negativo
- Imutabilidade do `editHistory`
- Geração de CSV

---

## 🛠️ Stack Tecnológica

| Componente | Tecnologia |
|------------|-----------|
| Linguagem | Java 17 (LTS) |
| Build | Maven |
| Serialização JSON | Jackson Databind 2.16 |
| Logging | SLF4J + Log4j2 |
| Testes | JUnit 5.10 |
