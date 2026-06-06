# Pyton Social

Um projeto Python completo para uma experiência musical de terminal. O aplicativo busca músicas na API do Deezer, recupera letras pelo Lyrics.ovh e apresenta a letra em diferentes modos interativos.

## Funcionalidades

- Busca de músicas por nome
- Exibição de lista de resultados
- Recupera letras da música
- Modos de exibição: meditação, aleatório, pulsante, sincopado
- CLI intuitiva com tema colorido e animações leves

## Como usar

1. Crie e ative um ambiente virtual:
   ```bash
   python -m venv .venv
   source .venv/bin/activate
   ```

2. Instale as dependências:
   ```bash
   pip install -r requirements.txt
   ```

3. Execute o aplicativo:
   ```bash
   python -m pyton_social
   ```

Ou, depois da instalação do pacote:

```bash
pyton-social
```

## Estrutura do projeto

- `src/pyton_social/`: código fonte do pacote
- `tests/`: testes unitários básicos
- `pyproject.toml`: metadados do projeto
- `requirements.txt`: dependências

## Observações

As APIs usadas são públicas e podem retornar resultados diferentes dependendo da disponibilidade e da música pesquisada.
