# Inova Tech — Inventory Management System

Sistema de gerenciamento de estoque desenvolvido em **Java 17** (com implementação paralela em **Python 3**), seguindo arquitetura profissional em camadas com padrões MVC e DAO. Inclui também uma interface web demonstrativa em HTML/CSS/JS.

---

## 🗂️ Estrutura do Repositório

```
├── src/main/java/br/com/inovatech/   # Implementação principal em Java
│   ├── model/          # POJOs e Enums (Product, User, Movement, Role, MovementType)
│   ├── repository/     # Interfaces DAO + implementações JSON (Jackson)
│   ├── service/        # Lógica de negócio (InventoryService, AuthService, ExportService)
│   ├── view/           # Interface CLI (MainCLI)
│   └── exception/      # Exceções personalizadas
│
├── inovatech_inventory.py   # Versão Python (dataclasses, threading, Decimal)
├── inventory_system.py      # Versão Python alternativa (stdlib only, sem dependências)
├── inventory_system.html    # Interface web demonstrativa (HTML/CSS/JS)
└── pom.xml                  # Build Maven
```

---

## 🚀 Como executar

### ▶️ Java (versão principal)

**Pré-requisitos:** JDK 17+ e Maven 3.8+

```bash
# Compilar
mvn clean package -DskipTests

# Executar
java -jar target/inventory-management-1.0.0.jar
```

### ▶️ Python (versão alternativa)

**Versão com dataclasses e threading (requer Python 3.10+):**
```bash
python inovatech_inventory.py
```

**Versão stdlib-only (sem dependências externas):**
```bash
python inventory_system.py
```

### ▶️ Interface Web

Abra `inventory_system.html` diretamente no navegador — não requer servidor.

---

## 🔐 Usuários padrão

| Usuário    | Senha    | Perfil     |
|------------|----------|------------|
| admin      | admin123 | GERENTE    |
| supervisor | super123 | SUPERVISOR |

> ⚠️ Altere as senhas em ambiente de produção!

---

## 📐 Padrões e boas práticas

| Requisito | Implementação |
|-----------|---------------|
| **RF01** Cadastro de Produtos | `Optional<Product>` (Java) / `Optional[Product]` (Python) para buscas seguras |
| **RF02** Autenticação | Hash SHA-256 via `java.security.MessageDigest` / `hashlib` |
| **RF03** Thread-safety | `AtomicInteger` + `synchronized` (Java) / `RLock` (Python) |
| **RF04** Alertas de Estoque | Padrão **Observer** (`StockAlertObserver` / callbacks `Callable`) |
| **RF05** Exportação | `java.nio.file` → `.csv` / `csv` module (Python) |
| **RN01** Precisão de preços | `BigDecimal` (Java) / `Decimal` com `ROUND_HALF_UP` (Python) |
| **RN02** Histórico imutável | `Collections.unmodifiableList()` (Java) / lista encapsulada (Python) |
| **PRD §8** Auto-save | Bloco `finally` no main loop |

---

## 🗃️ Persistência

Dados salvos em `data/`:

| Arquivo | Conteúdo |
|---|---|
| `data/produtos.json` | Catálogo de produtos |
| `data/movimentacoes.json` | Histórico de movimentações |
| `data/usuarios.json` | Usuários do sistema |

Exportações em `exports/`:
- `exports/produtos_YYYYMMDD_HHmmss.csv`
- `exports/movimentacoes_YYYYMMDD_HHmmss.csv`

### Estrutura JSON de produto

```json
{
  "products": [
    {
      "id": 101,
      "sku": "IPH-15-PRO",
      "name": "iPhone 15 Pro Max",
      "cost_price": "4500.00",
      "selling_price": "8999.90",
      "currentQuantity": 15,
      "minThreshold": 5,
      "active": true,
      "edit_history": ["01/06/2025 10:00:00 Produto criado."]
    }
  ]
}
```

---

## 🧪 Cobertura de Testes (JUnit 5)

```bash
mvn test
```

Casos cobertos:

- Cadastro com SKU duplicado → `ProdutoDuplicadoException`
- Validação de preço de venda menor que custo
- Registro de movimentações: entrada, saída e ajuste
- `EstoqueInsuficienteException` para saldo insuficiente
- Alertas de estoque baixo via padrão Observer
- **Thread-safety**: 20 threads concorrentes sem saldo negativo
- Imutabilidade do `editHistory`
- Geração de arquivo `.csv`

---

## 🛠️ Stack Tecnológica

### Java (versão principal)

| Componente | Tecnologia |
|------------|------------|
| Linguagem | Java 17 (LTS) |
| Build | Maven 3.8+ |
| Serialização JSON | Jackson Databind 2.16.1 + `jackson-datatype-jsr310` |
| Logging | SLF4J 2.0.12 + Log4j2 2.23.0 |
| Testes | JUnit 5.10.2 |
| Entry point | `br.com.inovatech.view.MainCLI` |

### Python (versão alternativa)

| Componente | Tecnologia |
|------------|------------|
| Linguagem | Python 3.10+ |
| Serialização | `json` (stdlib) |
| Concorrência | `threading.RLock` |
| Precisão numérica | `decimal.Decimal` |
| Dependências externas | **Nenhuma** (`inventory_system.py`) |

---

## ⚙️ Funcionalidades da CLI

```
[1] Cadastro      → cadastrar, editar, desativar produtos
[2] Movimentações → entrada, saída, ajuste de estoque
[3] Consultas     → busca por ID, SKU, nome; listar estoque baixo
[4] Relatórios    → produtos ativos, movimentações por SKU
[5] Exportação    → gerar CSV de produtos e movimentações
[0] Sair          → auto-save automático no bloco finally
```

---

## 🎓 Contexto Acadêmico

Projeto desenvolvido como trabalho universitário. A **Inova Tech** é uma empresa fictícia criada exclusivamente para fins acadêmicos.
