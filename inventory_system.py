#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Inova Tech - Inventory Management System
Versão Python com apenas bibliotecas padrão (sem dependências externas)
"""

import json
import hashlib
import os
from datetime import datetime
from pathlib import Path
from typing import Optional, List, Dict, Any
from decimal import Decimal
from enum import Enum

# Configuração de diretórios
DATA_DIR = Path("data")
EXPORT_DIR = Path("exports")
PRODUCTS_FILE = DATA_DIR / "products.json"
MOVEMENTS_FILE = DATA_DIR / "movements.json"
USERS_FILE = DATA_DIR / "users.json"


# ============= ENUMS =============

class Role(Enum):
    ADMIN = "ADMIN"
    SUPERVISOR = "SUPERVISOR"


class MovementType(Enum):
    ENTRADA = "ENTRADA"
    SAIDA = "SAIDA"
    AJUSTE = "AJUSTE"


# ============= EXCEÇÕES =============

class AuthException(Exception):
    pass


class StockException(Exception):
    pass


class ProductException(Exception):
    pass


# ============= MODELOS =============

class Product:
    def __init__(self, id: int, sku: str, name: str, cost_price: float, 
                 selling_price: float, quantity: int, min_threshold: int):
        self.id = id
        self.sku = sku.upper().strip()
        self.name = name.strip()
        self.cost_price = float(cost_price)
        self.selling_price = float(selling_price)
        self.quantity = int(quantity)
        self.min_threshold = int(min_threshold)
        self.active = True
        self.history = [self._timestamp() + " Produto criado."]

    def _timestamp(self) -> str:
        return datetime.now().strftime("%d/%m/%Y %H:%M:%S")

    def add_quantity(self, amount: int) -> None:
        if amount <= 0:
            raise ValueError("Quantidade deve ser positiva")
        self.quantity += amount
        self.history.append(f"{self._timestamp()} Entrada de {amount} unidades.")

    def remove_quantity(self, amount: int) -> None:
        if amount <= 0:
            raise ValueError("Quantidade deve ser positiva")
        if self.quantity < amount:
            raise StockException(f"Estoque insuficiente. Disponível: {self.quantity}, Solicitado: {amount}")
        self.quantity -= amount
        self.history.append(f"{self._timestamp()} Saída de {amount} unidades.")

    def adjust_quantity(self, new_amount: int) -> None:
        old = self.quantity
        if new_amount < 0:
            raise ValueError("Quantidade não pode ser negativa")
        self.quantity = new_amount
        self.history.append(f"{self._timestamp()} Ajuste: {old} → {new_amount} unidades.")

    def is_low_stock(self) -> bool:
        return self.quantity < self.min_threshold

    def profit_margin(self) -> float:
        if self.cost_price == 0:
            return 0.0
        return ((self.selling_price - self.cost_price) / self.cost_price) * 100

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "sku": self.sku,
            "name": self.name,
            "cost_price": self.cost_price,
            "selling_price": self.selling_price,
            "quantity": self.quantity,
            "min_threshold": self.min_threshold,
            "active": self.active,
            "history": self.history
        }

    @staticmethod
    def from_dict(data: Dict[str, Any]) -> "Product":
        p = Product(
            data["id"], data["sku"], data["name"],
            data["cost_price"], data["selling_price"],
            data["quantity"], data["min_threshold"]
        )
        p.active = data.get("active", True)
        p.history = data.get("history", p.history)
        return p


class Movement:
    def __init__(self, type: MovementType, sku: str, quantity: int, 
                 username: str, reason: str = "Sem motivo"):
        self.timestamp = datetime.now().isoformat()
        self.type = type.value
        self.sku = sku
        self.quantity = quantity
        self.username = username
        self.reason = reason

    def to_dict(self) -> Dict[str, Any]:
        return {
            "timestamp": self.timestamp,
            "type": self.type,
            "sku": self.sku,
            "quantity": self.quantity,
            "username": self.username,
            "reason": self.reason
        }

    @staticmethod
    def from_dict(data: Dict[str, Any]) -> "Movement":
        m = Movement(MovementType(data["type"]), data["sku"], data["quantity"],
                     data["username"], data.get("reason", "Sem motivo"))
        m.timestamp = data["timestamp"]
        return m

    def __str__(self) -> str:
        dt = datetime.fromisoformat(self.timestamp).strftime("%d/%m/%Y %H:%M:%S")
        return f"[{dt}] {self.type} | SKU: {self.sku} | Qtd: {self.quantity} | Por: {self.username}"


class User:
    def __init__(self, username: str, password_hash: str, role: Role):
        self.username = username.lower().strip()
        self.password_hash = password_hash
        self.role = role
        self.active = True

    def to_dict(self) -> Dict[str, Any]:
        return {
            "username": self.username,
            "password_hash": self.password_hash,
            "role": self.role.value,
            "active": self.active
        }

    @staticmethod
    def from_dict(data: Dict[str, Any]) -> "User":
        u = User(data["username"], data["password_hash"], Role(data["role"]))
        u.active = data.get("active", True)
        return u


# ============= REPOSITÓRIOS =============

class ProductRepository:
    def __init__(self, filepath: Path):
        self.filepath = filepath
        self.products: Dict[int, Product] = {}
        self.next_id = 1
        self.load()

    def load(self) -> None:
        if not self.filepath.exists():
            return
        with open(self.filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        for item in data.get("products", []):
            p = Product.from_dict(item)
            self.products[p.id] = p
            self.next_id = max(self.next_id, p.id + 1)

    def save_all(self) -> None:
        self.filepath.parent.mkdir(parents=True, exist_ok=True)
        data = {"products": [p.to_dict() for p in sorted(self.products.values(), key=lambda x: x.id)]}
        with open(self.filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

    def add(self, product: Product) -> None:
        if product.id is None:
            product.id = self.next_id
            self.next_id += 1
        self.products[product.id] = product

    def get(self, id: int) -> Optional[Product]:
        return self.products.get(id)

    def get_by_sku(self, sku: str) -> Optional[Product]:
        sku = sku.upper().strip()
        for p in self.products.values():
            if p.sku == sku:
                return p
        return None

    def get_all(self) -> List[Product]:
        return sorted(self.products.values(), key=lambda x: x.id)

    def get_active(self) -> List[Product]:
        return sorted([p for p in self.products.values() if p.active], key=lambda x: x.name)

    def get_low_stock(self) -> List[Product]:
        return sorted([p for p in self.products.values() if p.active and p.is_low_stock()], key=lambda x: x.name)


class MovementRepository:
    def __init__(self, filepath: Path):
        self.filepath = filepath
        self.movements: List[Movement] = []
        self.load()

    def load(self) -> None:
        if not self.filepath.exists():
            return
        with open(self.filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        self.movements = [Movement.from_dict(item) for item in data.get("movements", [])]

    def save_all(self) -> None:
        self.filepath.parent.mkdir(parents=True, exist_ok=True)
        data = {"movements": [m.to_dict() for m in self.movements]}
        with open(self.filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

    def add(self, movement: Movement) -> None:
        self.movements.append(movement)

    def get_all(self) -> List[Movement]:
        return list(self.movements)

    def get_by_sku(self, sku: str) -> List[Movement]:
        sku = sku.upper().strip()
        return [m for m in self.movements if m.sku == sku]


class UserRepository:
    def __init__(self, filepath: Path):
        self.filepath = filepath
        self.users: Dict[str, User] = {}
        self.load()

    def load(self) -> None:
        if not self.filepath.exists():
            return
        with open(self.filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        for item in data.get("users", []):
            u = User.from_dict(item)
            self.users[u.username] = u

    def save_all(self) -> None:
        self.filepath.parent.mkdir(parents=True, exist_ok=True)
        data = {"users": [u.to_dict() for u in self.users.values()]}
        with open(self.filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

    def add(self, user: User) -> None:
        self.users[user.username] = user

    def get(self, username: str) -> Optional[User]:
        return self.users.get(username.lower().strip())

    def get_all(self) -> List[User]:
        return list(self.users.values())

    def exists(self, username: str) -> bool:
        return username.lower().strip() in self.users


# ============= SERVIÇOS =============

class AuthService:
    def __init__(self, user_repo: UserRepository):
        self.user_repo = user_repo
        self.current_user: Optional[User] = None
        self._init_default_users()

    def _init_default_users(self) -> None:
        if not self.user_repo.get_all():
            self.user_repo.add(User("admin", self._hash_password("admin123"), Role.ADMIN))
            self.user_repo.add(User("supervisor", self._hash_password("super123"), Role.SUPERVISOR))
            self.user_repo.save_all()

    @staticmethod
    def _hash_password(password: str) -> str:
        return hashlib.sha256(password.encode()).hexdigest()

    def login(self, username: str, password: str) -> User:
        user = self.user_repo.get(username)
        if not user:
            raise AuthException("Usuário não encontrado")
        if not user.active:
            raise AuthException("Conta desativada")
        if user.password_hash != self._hash_password(password):
            raise AuthException("Senha incorreta")
        self.current_user = user
        return user

    def logout(self) -> None:
        self.current_user = None

    def get_current(self) -> Optional[User]:
        return self.current_user

    def is_logged(self) -> bool:
        return self.current_user is not None

    def require_admin(self) -> None:
        if not self.is_logged() or self.current_user.role != Role.ADMIN:
            raise AuthException("Acesso negado: requer perfil ADMIN")


class InventoryService:
    def __init__(self, product_repo: ProductRepository, movement_repo: MovementRepository):
        self.product_repo = product_repo
        self.movement_repo = movement_repo
        self.alerts: List[callable] = []

    def add_alert(self, callback: callable) -> None:
        self.alerts.append(callback)

    def _notify_alerts(self, product: Product) -> None:
        if product.is_low_stock():
            for callback in self.alerts:
                callback(product)

    def create_product(self, sku: str, name: str, cost: float, sell: float, 
                       qty: int, min_threshold: int) -> Product:
        if self.product_repo.get_by_sku(sku):
            raise ProductException(f"SKU já existe: {sku}")
        if sell < cost:
            raise ProductException("Preço de venda não pode ser menor que custo")
        
        product = Product(None, sku, name, cost, sell, qty, min_threshold)
        self.product_repo.add(product)
        self._notify_alerts(product)
        return product

    def update_product(self, id: int, name: str, cost: float, sell: float, min_threshold: int) -> Product:
        product = self.product_repo.get(id)
        if not product:
            raise ProductException("Produto não encontrado")
        product.name = name
        product.cost_price = cost
        product.selling_price = sell
        product.min_threshold = min_threshold
        return product

    def register_entry(self, product_id: int, qty: int, reason: str, username: str) -> None:
        product = self.product_repo.get(product_id)
        if not product:
            raise ProductException("Produto não encontrado")
        product.add_quantity(qty)
        self.movement_repo.add(Movement(MovementType.ENTRADA, product.sku, qty, username, reason))
        self._notify_alerts(product)

    def register_exit(self, product_id: int, qty: int, reason: str, username: str) -> None:
        product = self.product_repo.get(product_id)
        if not product:
            raise ProductException("Produto não encontrado")
        product.remove_quantity(qty)
        self.movement_repo.add(Movement(MovementType.SAIDA, product.sku, qty, username, reason))
        self._notify_alerts(product)

    def register_adjustment(self, product_id: int, new_qty: int, reason: str, username: str) -> None:
        product = self.product_repo.get(product_id)
        if not product:
            raise ProductException("Produto não encontrado")
        product.adjust_quantity(new_qty)
        diff = new_qty - product.quantity
        self.movement_repo.add(Movement(MovementType.AJUSTE, product.sku, diff, username, reason))
        self._notify_alerts(product)

    def export_products_csv(self) -> str:
        lines = ["ID,SKU,Nome,Preço Custo,Preço Venda,Qtd,Mínimo,Margem %,Status,Alerta"]
        for p in self.product_repo.get_active():
            status = "ATIVO" if p.active else "INATIVO"
            alert = "BAIXO" if p.is_low_stock() else "OK"
            margin = f"{p.profit_margin():.2f}"
            lines.append(f'{p.id},{p.sku},"{p.name}",{p.cost_price},{p.selling_price},{p.quantity},{p.min_threshold},{margin},{status},{alert}')
        return "\n".join(lines)

    def export_movements_csv(self) -> str:
        lines = ["Data/Hora,Tipo,SKU,Qtd,Usuário,Motivo"]
        for m in self.movement_repo.get_all():
            dt = datetime.fromisoformat(m.timestamp).strftime("%d/%m/%Y %H:%M:%S")
            lines.append(f'{dt},{m.type},{m.sku},{m.quantity},{m.username},"{m.reason}"')
        return "\n".join(lines)

    def save_all(self) -> None:
        self.product_repo.save_all()
        self.movement_repo.save_all()


# ============= CLI =============

class MainCLI:
    def __init__(self):
        self.product_repo = ProductRepository(PRODUCTS_FILE)
        self.movement_repo = MovementRepository(MOVEMENTS_FILE)
        self.user_repo = UserRepository(USERS_FILE)
        self.auth = AuthService(self.user_repo)
        self.inventory = InventoryService(self.product_repo, self.movement_repo)
        self.inventory.add_alert(self._alert_low_stock)

    def _alert_low_stock(self, product: Product) -> None:
        print(f"\n⚠️  ALERTA: Estoque baixo em '{product.name}' (SKU: {product.sku}) - {product.quantity}/{product.min_threshold}")

    def run(self) -> None:
        self._print_banner()
        if not self._login():
            print("Sistema encerrado.")
            return
        try:
            self._main_loop()
        finally:
            self.inventory.save_all()
            self.user_repo.save_all()
            print("\n✅ Dados salvos. Até logo!")

    def _print_banner(self) -> None:
        print("""
╔════════════════════════════════════════╗
║  Inova Tech - Inventory Management     ║
║         Python (Stdlib Only)           ║
╚════════════════════════════════════════╝
        """)

    def _login(self) -> bool:
        print("\n▶ LOGIN")
        for attempt in range(3):
            username = input("Usuário: ").strip()
            password = input("Senha  : ").strip()
            try:
                user = self.auth.login(username, password)
                print(f"✅ Bem-vindo, {user.username}! ({user.role.value})\n")
                return True
            except AuthException as e:
                print(f"❌ {e}")
        print("Limite de tentativas atingido.")
        return False

    def _main_loop(self) -> None:
        while True:
            print("""
┌─ MENU PRINCIPAL ─────────────────┐
│ [1] Cadastro de Produtos         │
│ [2] Movimentações de Estoque     │
│ [3] Consultas                    │
│ [4] Relatórios                   │
│ [5] Exportação                   │
│ [0] Sair                         │
└──────────────────────────────────┘
            """)
            opcao = input("Opção: ").strip()
            if opcao == "1":
                self._menu_cadastro()
            elif opcao == "2":
                self._menu_movimentacoes()
            elif opcao == "3":
                self._menu_consultas()
            elif opcao == "4":
                self._menu_relatorios()
            elif opcao == "5":
                self._menu_exportacao()
            elif opcao == "0":
                break
            else:
                print("Opção inválida!")

    def _menu_cadastro(self) -> None:
        while True:
            print("""
┌─ CADASTRO DE PRODUTOS ───────────┐
│ [1] Novo Produto                 │
│ [2] Editar Produto               │
│ [3] Desativar Produto            │
│ [0] Voltar                       │
└──────────────────────────────────┘
            """)
            opcao = input("Opção: ").strip()
            if opcao == "1":
                self._cadastrar_produto()
            elif opcao == "2":
                self._editar_produto()
            elif opcao == "3":
                self._desativar_produto()
            elif opcao == "0":
                return
            else:
                print("Opção inválida!")

    def _cadastrar_produto(self) -> None:
        try:
            sku = input("SKU         : ").strip()
            name = input("Nome        : ").strip()
            cost = float(input("Preço custo : "))
            sell = float(input("Preço venda : "))
            qty = int(input("Quantidade  : "))
            min_thr = int(input("Mínimo      : "))
            product = self.inventory.create_product(sku, name, cost, sell, qty, min_thr)
            print(f"✅ Produto criado com ID={product.id}")
        except ProductException as e:
            print(f"❌ {e}")
        except ValueError:
            print("❌ Valor inválido")

    def _editar_produto(self) -> None:
        try:
            id = int(input("ID do produto: "))
            product = self.product_repo.get(id)
            if not product:
                print("❌ Não encontrado")
                return
            print(f"Editando: {product.name} ({product.sku})")
            name = input(f"Nome [{product.name}]: ").strip() or product.name
            cost = float(input(f"Custo [{product.cost_price}]: ") or product.cost_price)
            sell = float(input(f"Venda [{product.selling_price}]: ") or product.selling_price)
            min_thr = int(input(f"Mínimo [{product.min_threshold}]: ") or product.min_threshold)
            self.inventory.update_product(id, name, cost, sell, min_thr)
            print("✅ Atualizado")
        except (ValueError, ProductException) as e:
            print(f"❌ {e}")

    def _desativar_produto(self) -> None:
        try:
            id = int(input("ID do produto: "))
            product = self.product_repo.get(id)
            if not product:
                print("❌ Não encontrado")
                return
            if input(f"Desativar '{product.name}'? (s/N): ").lower() == "s":
                product.active = False
                product.history.append(datetime.now().strftime("%d/%m/%Y %H:%M:%S") + " Desativado.")
                print("✅ Desativado")
        except ValueError:
            print("❌ ID inválido")

    def _menu_movimentacoes(self) -> None:
        print("""
┌─ MOVIMENTAÇÕES ──────────────────┐
│ [1] Entrada                      │
│ [2] Saída                        │
│ [3] Ajuste                       │
│ [0] Voltar                       │
└──────────────────────────────────┘
        """)
        opcao = input("Opção: ").strip()
        if opcao == "1":
            self._registrar_entrada()
        elif opcao == "2":
            self._registrar_saida()
        elif opcao == "3":
            self._registrar_ajuste()

    def _registrar_entrada(self) -> None:
        try:
            id = int(input("ID do produto: "))
            qty = int(input("Quantidade  : "))
            reason = input("Motivo      : ") or "Sem motivo"
            self.inventory.register_entry(id, qty, reason, self.auth.current_user.username)
            product = self.product_repo.get(id)
            print(f"✅ Entrada registrada. Saldo: {product.quantity}")
        except (ValueError, StockException, ProductException) as e:
            print(f"❌ {e}")

    def _registrar_saida(self) -> None:
        try:
            id = int(input("ID do produto: "))
            qty = int(input("Quantidade  : "))
            reason = input("Motivo      : ") or "Sem motivo"
            self.inventory.register_exit(id, qty, reason, self.auth.current_user.username)
            product = self.product_repo.get(id)
            print(f"✅ Saída registrada. Saldo: {product.quantity}")
        except (ValueError, StockException, ProductException) as e:
            print(f"❌ {e}")

    def _registrar_ajuste(self) -> None:
        try:
            id = int(input("ID do produto: "))
            new_qty = int(input("Nova qtd    : "))
            reason = input("Motivo      : ") or "Sem motivo"
            product = self.product_repo.get(id)
            if product:
                old_qty = product.quantity
                product.adjust_quantity(new_qty)
                diff = new_qty - old_qty
                self.movement_repo.add(Movement(MovementType.AJUSTE, product.sku, diff, self.auth.current_user.username, reason))
                print(f"✅ Ajuste registrado. {old_qty} → {new_qty}")
        except (ValueError, ProductException) as e:
            print(f"❌ {e}")

    def _menu_consultas(self) -> None:
        print("""
┌─ CONSULTAS ──────────────────────┐
│ [1] Listar todos                 │
│ [2] Buscar por ID                │
│ [3] Buscar por SKU               │
│ [4] Estoque baixo                │
│ [5] Movimentações                │
│ [0] Voltar                       │
└──────────────────────────────────┘
        """)
        opcao = input("Opção: ").strip()
        if opcao == "1":
            self._listar_produtos()
        elif opcao == "2":
            self._buscar_por_id()
        elif opcao == "3":
            self._buscar_por_sku()
        elif opcao == "4":
            self._listar_estoque_baixo()
        elif opcao == "5":
            self._listar_movimentos()

    def _listar_produtos(self) -> None:
        produtos = self.product_repo.get_all()
        if not produtos:
            print("Nenhum produto registrado.")
            return
        for p in produtos:
            self._print_product(p)

    def _buscar_por_id(self) -> None:
        try:
            id = int(input("ID: "))
            p = self.product_repo.get(id)
            if p:
                self._print_product(p)
            else:
                print("❌ Não encontrado")
        except ValueError:
            print("❌ ID inválido")

    def _buscar_por_sku(self) -> None:
        sku = input("SKU: ").strip()
        p = self.product_repo.get_by_sku(sku)
        if p:
            self._print_product(p)
        else:
            print("❌ Não encontrado")

    def _listar_estoque_baixo(self) -> None:
        produtos = self.product_repo.get_low_stock()
        if not produtos:
            print("Nenhum produto em estoque baixo.")
            return
        for p in produtos:
            self._print_product(p)

    def _listar_movimentos(self) -> None:
        movimentos = self.movement_repo.get_all()
        if not movimentos:
            print("Nenhuma movimentação registrada.")
            return
        for m in movimentos:
            print(m)

    def _print_product(self, p: Product) -> None:
        alert = " ⚠️  BAIXO" if p.is_low_stock() else ""
        print(f"ID={p.id:3} | SKU={p.sku:12} | {p.name:30} | Qtd={p.quantity:5} | Min={p.min_threshold:3}{alert}")

    def _menu_relatorios(self) -> None:
        print("""
┌─ RELATÓRIOS ─────────────────────┐
│ [1] Produtos ativos              │
│ [2] Movimentos por SKU           │
│ [0] Voltar                       │
└──────────────────────────────────┘
        """)
        opcao = input("Opção: ").strip()
        if opcao == "1":
            self._listar_produtos()
        elif opcao == "2":
            sku = input("SKU: ").strip()
            movimentos = self.movement_repo.get_by_sku(sku)
            if not movimentos:
                print(f"Nenhuma movimentação para SKU {sku}")
                return
            for m in movimentos:
                print(m)

    def _menu_exportacao(self) -> None:
        print("""
┌─ EXPORTAÇÃO ─────────────────────┐
│ [1] Produtos CSV                 │
│ [2] Movimentos CSV               │
│ [0] Voltar                       │
└──────────────────────────────────┘
        """)
        opcao = input("Opção: ").strip()
        if opcao == "1":
            csv = self.inventory.export_products_csv()
            path = EXPORT_DIR / f"products_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
            EXPORT_DIR.mkdir(parents=True, exist_ok=True)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(csv)
            print(f"✅ Exportado em: {path}")
        elif opcao == "2":
            csv = self.inventory.export_movements_csv()
            path = EXPORT_DIR / f"movements_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
            EXPORT_DIR.mkdir(parents=True, exist_ok=True)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(csv)
            print(f"✅ Exportado em: {path}")


# ============= MAIN =============

if __name__ == "__main__":
    cli = MainCLI()
    cli.run()
