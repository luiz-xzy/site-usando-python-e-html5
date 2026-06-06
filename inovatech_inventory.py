from __future__ import annotations

import hashlib
import json
import os
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal, InvalidOperation, getcontext, ROUND_HALF_UP
from enum import Enum
from pathlib import Path
from threading import RLock
from typing import Callable, Dict, List, Optional

getcontext().prec = 28
getcontext().rounding = ROUND_HALF_UP

DATA_DIR = Path("data")
EXPORT_DIR = Path("exports")
PRODUCTS_FILE = DATA_DIR / "produtos.json"
MOVEMENTS_FILE = DATA_DIR / "movimentacoes.json"
USERS_FILE = DATA_DIR / "usuarios.json"


class AutenticacaoException(Exception):
    pass


class EstoqueInsuficienteException(Exception):
    pass


class ProdutoDuplicadoException(Exception):
    pass


class Role(Enum):
    GERENTE = "GERENTE"
    SUPERVISOR = "SUPERVISOR"

    def descricao(self) -> str:
        return self.value


class MovementType(Enum):
    ENTRADA = "ENTRADA"
    SAIDA = "SAIDA"
    AJUSTE = "AJUSTE"


@dataclass
class User:
    username: str
    password_hash: str
    role: Role
    active: bool = True

    def to_dict(self) -> dict:
        return {
            "username": self.username,
            "password_hash": self.password_hash,
            "role": self.role.value,
            "active": self.active,
        }

    @staticmethod
    def from_dict(data: dict) -> "User":
        return User(
            username=data["username"],
            password_hash=data["password_hash"],
            role=Role(data["role"]),
            active=data.get("active", True),
        )


@dataclass
class Product:
    id: int
    sku: str
    name: str
    cost_price: Decimal
    selling_price: Decimal
    current_quantity: int
    min_threshold: int
    active: bool = True
    edit_history: List[str] = field(default_factory=list)

    def __post_init__(self):
        self.sku = self.sku.strip().upper()
        self.name = self.name.strip()
        if self.cost_price < 0:
            raise ValueError("Preço de custo inválido.")
        if self.selling_price <= 0:
            raise ValueError("Preço de venda deve ser positivo.")
        if self.min_threshold < 0:
            raise ValueError("Limiar mínimo inválido.")
        if self.current_quantity < 0:
            raise ValueError("Quantidade atual não pode ser negativa.")
        if not self.edit_history:
            self.add_history_entry("Produto criado.")

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "sku": self.sku,
            "name": self.name,
            "cost_price": str(self.cost_price),
            "selling_price": str(self.selling_price),
            "current_quantity": self.current_quantity,
            "min_threshold": self.min_threshold,
            "active": self.active,
            "edit_history": self.edit_history,
        }

    @staticmethod
    def from_dict(data: dict) -> "Product":
        return Product(
            id=int(data["id"]),
            sku=data["sku"],
            name=data["name"],
            cost_price=Decimal(data["cost_price"]),
            selling_price=Decimal(data["selling_price"]),
            current_quantity=int(data["current_quantity"]),
            min_threshold=int(data["min_threshold"]),
            active=bool(data.get("active", True)),
            edit_history=list(data.get("edit_history", [])),
        )

    def add_quantity(self, amount: int) -> int:
        if amount <= 0:
            raise ValueError("Quantidade de entrada deve ser positiva.")
        self.current_quantity += amount
        return self.current_quantity

    def subtract_quantity(self, amount: int) -> bool:
        if amount <= 0:
            raise ValueError("Quantidade de saída deve ser positiva.")
        if self.current_quantity < amount:
            return False
        self.current_quantity -= amount
        return True

    def set_current_quantity(self, quantity: int) -> None:
        if quantity < 0:
            raise ValueError("Quantidade não pode ser negativa.")
        self.current_quantity = quantity

    def is_low_stock(self) -> bool:
        return self.current_quantity < self.min_threshold

    def profit_margin(self) -> Decimal:
        if self.cost_price == 0:
            return Decimal("0")
        return ((self.selling_price - self.cost_price) / self.cost_price * Decimal("100")).quantize(Decimal("0.01"))

    def add_history_entry(self, entry: str) -> None:
        timestamp = datetime.now().strftime("%d/%m/%Y %H:%M:%S")
        self.edit_history.append(f"[{timestamp}] {entry}")


@dataclass
class Movement:
    timestamp: datetime
    type: MovementType
    product_sku: str
    quantity: int
    responsible: str
    reason: str

    def to_dict(self) -> dict:
        return {
            "timestamp": self.timestamp.isoformat(),
            "type": self.type.value,
            "product_sku": self.product_sku,
            "quantity": self.quantity,
            "responsible": self.responsible,
            "reason": self.reason,
        }

    @staticmethod
    def from_dict(data: dict) -> "Movement":
        return Movement(
            timestamp=datetime.fromisoformat(data["timestamp"]),
            type=MovementType(data["type"]),
            product_sku=data["product_sku"],
            quantity=int(data["quantity"]),
            responsible=data["responsible"],
            reason=data.get("reason", "Sem observação."),
        )

    @staticmethod
    def create(type: MovementType, product_sku: str, quantity: int, responsible: str, reason: str) -> "Movement":
        return Movement(
            timestamp=datetime.now(),
            type=type,
            product_sku=product_sku,
            quantity=quantity,
            responsible=responsible,
            reason=reason.strip() or "Sem observação.",
        )

    def __str__(self) -> str:
        return (
            f"[{self.timestamp.strftime('%d/%m/%Y %H:%M:%S')}] "
            f"{self.type.value} | SKU: {self.product_sku} | Qtd: {self.quantity} | "
            f"Por: {self.responsible} | Motivo: {self.reason}"
        )


class JsonProductRepository:
    def __init__(self, path: Path):
        self.path = path
        self._store: Dict[int, Product] = {}
        self._next_id = 1
        self.load()

    def load(self) -> None:
        if not self.path.exists():
            return
        with self.path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        for item in data.get("products", []):
            product = Product.from_dict(item)
            self._store[product.id] = product
            self._next_id = max(self._next_id, product.id + 1)

    def save(self, product: Product) -> None:
        if product.id is None:
            product.id = self.next_id()
        self._store[product.id] = product

    def find_by_id(self, id_: int) -> Optional[Product]:
        return self._store.get(id_)

    def find_by_sku(self, sku: str) -> Optional[Product]:
        if sku is None:
            return None
        normalized = sku.strip().upper()
        return next((p for p in self._store.values() if p.sku == normalized), None)

    def find_by_name(self, term: str) -> List[Product]:
        if not term:
            return self.find_all()
        term_lower = term.lower()
        return sorted(
            [p for p in self._store.values() if term_lower in p.name.lower()],
            key=lambda p: p.name,
        )

    def find_all(self) -> List[Product]:
        return sorted(self._store.values(), key=lambda p: p.id)

    def find_all_active(self) -> List[Product]:
        return sorted([p for p in self._store.values() if p.active], key=lambda p: p.name)

    def find_low_stock(self) -> List[Product]:
        return sorted([p for p in self._store.values() if p.active and p.is_low_stock()], key=lambda p: p.name)

    def delete(self, id_: int) -> None:
        product = self.find_by_id(id_)
        if product:
            product.active = False
            product.add_history_entry("Produto desativado (exclusão lógica).")

    def next_id(self) -> int:
        current = self._next_id
        self._next_id += 1
        return current

    def flush(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        data = {"products": [product.to_dict() for product in self.find_all()]}
        with self.path.open("w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)


class JsonMovementRepository:
    def __init__(self, path: Path):
        self.path = path
        self._movements: List[Movement] = []
        self.load()

    def load(self) -> None:
        if not self.path.exists():
            return
        with self.path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        self._movements = [Movement.from_dict(item) for item in data.get("movements", [])]

    def save(self, movement: Movement) -> None:
        self._movements.append(movement)

    def find_all(self) -> List[Movement]:
        return list(self._movements)

    def find_by_sku(self, sku: str) -> List[Movement]:
        normalized = sku.strip().upper()
        return [m for m in self._movements if m.product_sku.upper() == normalized]

    def flush(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        data = {"movements": [movement.to_dict() for movement in self._movements]}
        with self.path.open("w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)


class JsonUserRepository:
    def __init__(self, path: Path):
        self.path = path
        self._store: Dict[str, User] = {}
        self.load()

    def load(self) -> None:
        if not self.path.exists():
            return
        with self.path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        for item in data.get("users", []):
            user = User.from_dict(item)
            self._store[user.username] = user

    def save(self, user: User) -> None:
        self._store[user.username] = user

    def find_by_username(self, username: str) -> Optional[User]:
        if username is None:
            return None
        return self._store.get(username.strip().lower())

    def exists_by_username(self, username: str) -> bool:
        return username.strip().lower() in self._store

    def find_all(self) -> List[User]:
        return list(self._store.values())

    def flush(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        data = {"users": [user.to_dict() for user in self._store.values()]}
        with self.path.open("w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)


class AuthService:
    def __init__(self, user_repo: JsonUserRepository):
        self.user_repo = user_repo
        self.current_user: Optional[User] = None
        self.seed_default_users()

    @staticmethod
    def hash_password(raw_password: str) -> str:
        digest = hashlib.sha256(raw_password.encode("utf-8")).hexdigest()
        return digest

    def seed_default_users(self) -> None:
        if not self.user_repo.find_all():
            self.user_repo.save(User("admin", self.hash_password("admin123"), Role.GERENTE))
            self.user_repo.save(User("supervisor", self.hash_password("super123"), Role.SUPERVISOR))
            self.user_repo.flush()

    def login(self, username: str, raw_password: str) -> User:
        if not username or not raw_password:
            raise AutenticacaoException("Usuário e senha são obrigatórios.")
        user = self.user_repo.find_by_username(username)
        if user is None:
            raise AutenticacaoException(f"Usuário não encontrado: {username}")
        if not user.active:
            raise AutenticacaoException("Conta desativada. Contate o administrador.")
        if user.password_hash != self.hash_password(raw_password):
            raise AutenticacaoException("Senha incorreta.")
        self.current_user = user
        return user

    def logout(self) -> None:
        self.current_user = None

    def get_current_user(self) -> Optional[User]:
        return self.current_user

    def is_logged_in(self) -> bool:
        return self.current_user is not None

    def require_role(self, required: Role) -> None:
        if not self.is_logged_in() or self.current_user.role != required:
            raise AutenticacaoException(f"Acesso negado. Operação restrita ao perfil: {required.value}")

    def create_user(self, username: str, raw_password: str, role: Role) -> User:
        self.require_role(Role.GERENTE)
        if self.user_repo.exists_by_username(username):
            raise ValueError(f"Usuário já existe: {username}")
        user = User(username.strip().lower(), self.hash_password(raw_password), role)
        self.user_repo.save(user)
        self.user_repo.flush()
        return user

    def change_password(self, username: str, new_raw_password: str) -> None:
        self.require_role(Role.GERENTE)
        user = self.user_repo.find_by_username(username)
        if user is None:
            raise ValueError(f"Usuário não encontrado: {username}")
        user.password_hash = self.hash_password(new_raw_password)
        self.user_repo.save(user)
        self.user_repo.flush()


class ExportService:
    def __init__(self, export_dir: Path):
        self.export_dir = export_dir

    def export_products_csv(self, products: List[Product]) -> Path:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        path = self.export_dir / f"produtos_{timestamp}.csv"
        self.export_dir.mkdir(parents=True, exist_ok=True)
        lines = [
            "ID,SKU,Nome,Preço Custo,Preço Venda,Qtd Atual,Limiar Mínimo,Margem %,Status,Alerta"
        ]
        for p in products:
            lines.append(
                ",".join([
                    str(p.id),
                    p.sku,
                    f'"{p.name.replace("\"", "\"\"")}"',
                    str(p.cost_price),
                    str(p.selling_price),
                    str(p.current_quantity),
                    str(p.min_threshold),
                    f"{p.profit_margin()}%",
                    "ATIVO" if p.active else "INATIVO",
                    "⚠ ESTOQUE BAIXO" if p.is_low_stock() else "OK",
                ])
            )
        path.write_text("\n".join(lines), encoding="utf-8")
        return path

    def export_movements_csv(self, movements: List[Movement]) -> Path:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        path = self.export_dir / f"movimentacoes_{timestamp}.csv"
        self.export_dir.mkdir(parents=True, exist_ok=True)
        lines = ["Data/Hora,Tipo,SKU,Quantidade,Responsável,Motivo"]
        for m in movements:
            lines.append(
                ",".join([
                    m.timestamp.strftime("%d/%m/%Y %H:%M:%S"),
                    m.type.value,
                    m.product_sku,
                    str(m.quantity),
                    m.responsible,
                    f'"{m.reason.replace("\"", "\"\"")}"',
                ])
            )
        path.write_text("\n".join(lines), encoding="utf-8")
        return path


class InventoryService:
    def __init__(self, product_repo: JsonProductRepository, movement_repo: JsonMovementRepository):
        self.product_repo = product_repo
        self.movement_repo = movement_repo
        self.alert_observers: List[Callable[[Product], None]] = []
        self.lock = RLock()

    def add_alert_observer(self, observer: Callable[[Product], None]) -> None:
        self.alert_observers.append(observer)

    def notify_low_stock(self, product: Product) -> None:
        if product.is_low_stock():
            for observer in self.alert_observers:
                observer(product)

    def cadastrar_produto(
        self,
        sku: str,
        name: str,
        cost_price: Decimal,
        selling_price: Decimal,
        initial_qty: int,
        min_threshold: int,
        responsible: User,
    ) -> Product:
        if selling_price < cost_price:
            raise ValueError("Preço de venda não pode ser menor que o preço de custo.")
        if self.product_repo.find_by_sku(sku):
            raise ProdutoDuplicadoException(sku)
        product = Product(
            id=self.product_repo.next_id(),
            sku=sku,
            name=name,
            cost_price=cost_price,
            selling_price=selling_price,
            current_quantity=initial_qty,
            min_threshold=min_threshold,
        )
        product.add_history_entry(f"Cadastrado por {responsible.username}.")
        self.product_repo.save(product)
        self.notify_low_stock(product)
        return product

    def atualizar_produto(
        self,
        id_: int,
        new_name: str,
        new_cost_price: Decimal,
        new_selling_price: Decimal,
        new_min_threshold: int,
        responsible: User,
    ) -> Product:
        product = self.get_or_throw(id_)
        product.add_history_entry(
            f"Dados atualizados por {responsible.username}: nome='{product.name}'→'{new_name}', "
            f"custo={product.cost_price}→{new_cost_price}, venda={product.selling_price}→{new_selling_price}, "
            f"limiar={product.min_threshold}→{new_min_threshold}"
        )
        product.name = new_name
        product.cost_price = new_cost_price
        product.selling_price = new_selling_price
        product.min_threshold = new_min_threshold
        self.product_repo.save(product)
        return product

    def registrar_entrada(self, product_id: int, qty: int, reason: str, responsible: User) -> Movement:
        with self.lock:
            product = self.get_or_throw(product_id)
            product.add_quantity(qty)
            product.add_history_entry(f"Entrada de {qty} un. por {responsible.username}. Motivo: {reason}")
            movement = Movement.create(MovementType.ENTRADA, product.sku, qty, responsible.username, reason)
            self.movement_repo.save(movement)
            return movement

    def registrar_saida(self, product_id: int, qty: int, reason: str, responsible: User) -> Movement:
        with self.lock:
            product = self.get_or_throw(product_id)
            if qty > product.current_quantity:
                raise EstoqueInsuficienteException(
                    f"Saldo insuficiente: solicitado {qty}, disponível {product.current_quantity}."
                )
            if not product.subtract_quantity(qty):
                raise EstoqueInsuficienteException(
                    f"Saldo insuficiente: solicitado {qty}, disponível {product.current_quantity}."
                )
            product.add_history_entry(f"Saída de {qty} un. por {responsible.username}. Motivo: {reason}")
            movement = Movement.create(MovementType.SAIDA, product.sku, qty, responsible.username, reason)
            self.movement_repo.save(movement)
            self.notify_low_stock(product)
            return movement

    def registrar_ajuste(self, product_id: int, new_qty: int, reason: str, responsible: User) -> Movement:
        with self.lock:
            product = self.get_or_throw(product_id)
            old_qty = product.current_quantity
            product.set_current_quantity(new_qty)
            product.add_history_entry(
                f"Ajuste de {old_qty} → {new_qty} un. por {responsible.username}. Motivo: {reason}"
            )
            movement = Movement.create(MovementType.AJUSTE, product.sku, new_qty - old_qty, responsible.username, reason)
            self.movement_repo.save(movement)
            self.notify_low_stock(product)
            return movement

    def listar_estoque_baixo(self) -> List[Product]:
        return self.product_repo.find_low_stock()

    def exportar_produtos_csv(self) -> str:
        lines = [
            "ID,SKU,Nome,Preço Custo,Preço Venda,Qtd Atual,Limiar Mínimo,Alerta"
        ]
        for p in self.product_repo.find_all_active():
            lines.append(
                ",".join([
                    str(p.id),
                    p.sku,
                    f'"{p.name.replace("\"", "\"\"")}"',
                    str(p.cost_price),
                    str(p.selling_price),
                    str(p.current_quantity),
                    str(p.min_threshold),
                    "BAIXO" if p.is_low_stock() else "OK",
                ])
            )
        return "\n".join(lines)

    def buscar_por_id(self, id_: int) -> Optional[Product]:
        return self.product_repo.find_by_id(id_)

    def buscar_por_sku(self, sku: str) -> Optional[Product]:
        return self.product_repo.find_by_sku(sku)

    def listar_todos(self) -> List[Product]:
        return self.product_repo.find_all()

    def listar_ativos(self) -> List[Product]:
        return self.product_repo.find_all_active()

    def buscar_por_nome(self, term: str) -> List[Product]:
        return self.product_repo.find_by_name(term)

    def listar_movimentos(self) -> List[Movement]:
        return self.movement_repo.find_all()

    def listar_movimentos_por_sku(self, sku: str) -> List[Movement]:
        return self.movement_repo.find_by_sku(sku)

    def auto_save(self) -> None:
        self.product_repo.flush()
        self.movement_repo.flush()

    def get_or_throw(self, id_: int) -> Product:
        product = self.product_repo.find_by_id(id_)
        if not product:
            raise ValueError(f"Produto não encontrado: ID={id_}")
        return product


class MainCLI:
    def __init__(self):
        self.user_repo = JsonUserRepository(USERS_FILE)
        self.product_repo = JsonProductRepository(PRODUCTS_FILE)
        self.movement_repo = JsonMovementRepository(MOVEMENTS_FILE)
        self.auth_service = AuthService(self.user_repo)
        self.inventory_service = InventoryService(self.product_repo, self.movement_repo)
        self.export_service = ExportService(EXPORT_DIR)
        self.inventory_service.add_alert_observer(self.print_low_stock_alert)

    def print_low_stock_alert(self, product: Product) -> None:
        print(
            f"⚠ ALERTA: Estoque baixo para '{product.name}' (SKU: {product.sku}) — "
            f"{product.current_quantity}/{product.min_threshold} unidades!"
        )

    def run(self) -> None:
        self.print_banner()
        if not self.do_login():
            print("Encerrando sistema.")
            return
        try:
            self.main_menu_loop()
        finally:
            self.inventory_service.auto_save()
            self.user_repo.flush()
            self.movement_repo.flush()
            print("\n✅ Dados salvos. Sistema encerrado. Até logo!")

    def print_banner(self) -> None:
        print("""
Inova Tech — Inventory Management System (Python)
===============================================
""")

    def do_login(self) -> bool:
        print("\n=== LOGIN ===")
        for _ in range(3):
            username = input("Usuário: ").strip()
            password = input("Senha  : ").strip()
            try:
                user = self.auth_service.login(username, password)
                print(f"✅ Bem-vindo, {user.username}! ({user.role.value})")
                return True
            except AutenticacaoException as exc:
                print(f"❌ {exc}")
        print("Número máximo de tentativas atingido.")
        return False

    def main_menu_loop(self) -> None:
        while True:
            print("""
[1] Cadastro de produtos
[2] Movimentações
[3] Consultas
[4] Relatórios
[5] Exportação
[0] Sair
""")
            opcao = input("Opção: ").strip()
            if opcao == "1":
                self.menu_cadastro()
            elif opcao == "2":
                self.menu_movimentacoes()
            elif opcao == "3":
                self.menu_consultas()
            elif opcao == "4":
                self.menu_relatorios()
            elif opcao == "5":
                self.menu_exportacao()
            elif opcao == "0":
                break
            else:
                print("Opção inválida.")

    def menu_cadastro(self) -> None:
        while True:
            print("""
── CADASTRO DE PRODUTOS ──────────────────
[1] Cadastrar novo produto
[2] Editar produto existente
[3] Desativar produto
[0] Voltar
""")
            opcao = input("Opção: ").strip()
            if opcao == "1":
                self.cadastrar_produto()
            elif opcao == "2":
                self.editar_produto()
            elif opcao == "3":
                self.desativar_produto()
            elif opcao == "0":
                return
            else:
                print("Opção inválida.")

    def cadastrar_produto(self) -> None:
        try:
            sku = input("SKU          : ").strip()
            name = input("Nome         : ").strip()
            cost_price = Decimal(input("Preço custo  : ").strip())
            selling_price = Decimal(input("Preço venda  : ").strip())
            initial_qty = int(input("Qtd inicial  : ").strip())
            min_threshold = int(input("Limiar mínimo: ").strip())
            product = self.inventory_service.cadastrar_produto(
                sku, name, cost_price, selling_price, initial_qty, min_threshold,
                self.auth_service.get_current_user(),
            )
            print(f"✅ Produto cadastrado com ID={product.id}")
        except ProdutoDuplicadoException as exc:
            print(f"❌ SKU já existe: {exc}")
        except (ValueError, InvalidOperation) as exc:
            print(f"❌ Erro: {exc}")

    def editar_produto(self) -> None:
        try:
            id_ = int(input("ID do produto: ").strip())
            product = self.inventory_service.buscar_por_id(id_)
            if not product:
                print("❌ Produto não encontrado.")
                return
            print(f"Editando: {product.name} (SKU: {product.sku})")
            name = input(f"Nome atual [{product.name}]: ").strip() or product.name
            cost_price_input = input(f"Preço custo atual [{product.cost_price}]: ").strip()
            cost_price = Decimal(cost_price_input) if cost_price_input else product.cost_price
            selling_price_input = input(f"Preço venda atual [{product.selling_price}]: ").strip()
            selling_price = Decimal(selling_price_input) if selling_price_input else product.selling_price
            min_threshold_input = input(f"Limiar mínimo atual [{product.min_threshold}]: ").strip()
            min_threshold = int(min_threshold_input) if min_threshold_input else product.min_threshold
            self.inventory_service.atualizar_produto(
                id_, name, cost_price, selling_price, min_threshold,
                self.auth_service.get_current_user(),
            )
            print("✅ Produto atualizado.")
        except (ValueError, InvalidOperation) as exc:
            print(f"❌ Erro: {exc}")

    def desativar_produto(self) -> None:
        try:
            id_ = int(input("ID do produto a desativar: ").strip())
            product = self.inventory_service.buscar_por_id(id_)
            if not product:
                print("❌ Produto não encontrado.")
                return
            confirm = input(f"Confirma desativar '{product.name}'? (s/N): ").strip().lower()
            if confirm == "s":
                self.product_repo.delete(id_)
                print("✅ Produto desativado.")
        except ValueError:
            print("❌ ID inválido.")

    def menu_movimentacoes(self) -> None:
        print("""
── MOVIMENTAÇÕES ─────────────────────────
[1] Registrar Entrada
[2] Registrar Saída
[3] Registrar Ajuste
[0] Voltar
""")
        opcao = input("Opção: ").strip()
        if opcao == "1":
            self.registrar_entrada()
        elif opcao == "2":
            self.registrar_saida()
        elif opcao == "3":
            self.registrar_ajuste()

    def selecionar_produto(self) -> Optional[Product]:
        try:
            id_ = int(input("ID do produto: ").strip())
            product = self.inventory_service.buscar_por_id(id_)
            if not product:
                print("❌ Produto não encontrado.")
            return product
        except ValueError:
            print("❌ ID inválido.")
            return None

    def registrar_entrada(self) -> None:
        product = self.selecionar_produto()
        if not product:
            return
        try:
            qty = int(input("Quantidade a entrada: ").strip())
            reason = input("Motivo              : ").strip()
            self.inventory_service.registrar_entrada(product.id, qty, reason, self.auth_service.get_current_user())
            print(f"✅ Entrada registrada. Saldo atual: {product.current_quantity}")
        except (ValueError, InvalidOperation) as exc:
            print(f"❌ Erro: {exc}")

    def registrar_saida(self) -> None:
        product = self.selecionar_produto()
        if not product:
            return
        try:
            qty = int(input("Quantidade a saída: ").strip())
            reason = input("Motivo              : ").strip()
            self.inventory_service.registrar_saida(product.id, qty, reason, self.auth_service.get_current_user())
            print(f"✅ Saída registrada. Saldo atual: {product.current_quantity}")
        except (ValueError, EstoqueInsuficienteException) as exc:
            print(f"❌ Erro: {exc}")

    def registrar_ajuste(self) -> None:
        product = self.selecionar_produto()
        if not product:
            return
        try:
            new_qty = int(input("Nova quantidade: ").strip())
            reason = input("Motivo         : ").strip()
            self.inventory_service.registrar_ajuste(product.id, new_qty, reason, self.auth_service.get_current_user())
            print(f"✅ Ajuste registrado. Saldo atual: {product.current_quantity}")
        except ValueError as exc:
            print(f"❌ Erro: {exc}")

    def menu_consultas(self) -> None:
        print("""
── CONSULTAS ─────────────────────────────
[1] Listar todos os produtos
[2] Buscar produto por ID
[3] Buscar produto por SKU
[4] Listar estoque baixo
[5] Listar movimentações
[0] Voltar
""")
        opcao = input("Opção: ").strip()
        if opcao == "1":
            self.listar_todos_produtos()
        elif opcao == "2":
            self.buscar_produto_por_id()
        elif opcao == "3":
            self.buscar_produto_por_sku()
        elif opcao == "4":
            self.listar_estoque_baixo()
        elif opcao == "5":
            self.listar_movimentacoes()

    def listar_todos_produtos(self) -> None:
        for product in self.inventory_service.listar_todos():
            self.print_product(product)

    def buscar_produto_por_id(self) -> None:
        try:
            id_ = int(input("ID do produto: ").strip())
            product = self.inventory_service.buscar_por_id(id_)
            if product:
                self.print_product(product)
            else:
                print("❌ Produto não encontrado.")
        except ValueError:
            print("❌ ID inválido.")

    def buscar_produto_por_sku(self) -> None:
        sku = input("SKU do produto: ").strip()
        product = self.inventory_service.buscar_por_sku(sku)
        if product:
            self.print_product(product)
        else:
            print("❌ Produto não encontrado.")

    def listar_estoque_baixo(self) -> None:
        products = self.inventory_service.listar_estoque_baixo()
        if not products:
            print("Nenhum produto em estoque baixo.")
            return
        for product in products:
            self.print_product(product)

    def listar_movimentacoes(self) -> None:
        for movement in self.inventory_service.listar_movimentos():
            print(movement)

    def print_product(self, product: Product) -> None:
        print(
            f"ID={product.id} | SKU={product.sku} | Nome={product.name} | "
            f"Qtd={product.current_quantity} | Min={product.min_threshold} | "
            f"Ativo={product.active} | Custo={product.cost_price} | Venda={product.selling_price}"
        )

    def menu_relatorios(self) -> None:
        print("""
── RELATÓRIOS ───────────────────────────
[1] Produtos ativos
[2] Movimentações por SKU
[0] Voltar
""")
        opcao = input("Opção: ").strip()
        if opcao == "1":
            self.listar_todos_produtos()
        elif opcao == "2":
            sku = input("SKU: ").strip()
            for movement in self.inventory_service.listar_movimentos_por_sku(sku):
                print(movement)

    def menu_exportacao(self) -> None:
        print("""
── EXPORTAÇÃO ───────────────────────────
[1] Exportar produtos CSV
[2] Exportar movimentações CSV
[0] Voltar
""")
        opcao = input("Opção: ").strip()
        if opcao == "1":
            path = self.export_service.export_products_csv(self.inventory_service.listar_ativos())
            print(f"✅ Exportado em: {path}")
        elif opcao == "2":
            path = self.export_service.export_movements_csv(self.inventory_service.listar_movimentos())
            print(f"✅ Exportado em: {path}")


if __name__ == "__main__":
    MainCLI().run()
