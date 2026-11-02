#!/usr/bin/env bash
# =============================================================================
# Cadastra produtos de exemplo no catálogo, para a busca e a paginação terem o
# que mostrar.
# =============================================================================
#
# Uso:
#   scripts/popular-produtos.sh                                   # API local, token do Keycloak local
#   API_URL=https://<servico>.onrender.com TOKEN=<jwt> scripts/popular-produtos.sh
#
# Variáveis:
#   API_URL  Endereço da API, sem o /api/v1. Padrão: http://localhost:8080
#   TOKEN    JWT com a audiência catalogo-api. Se faltar e a API for local, o
#            script pede um ao Keycloak do docker-compose.yml.
#
# Pode rodar mais de uma vez: produto cujo SKU já existe devolve 409 e é só
# pulado. Precisa de curl (e de jq só para buscar o token no Keycloak local).

set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
API_URL="${API_URL%/}"
ENDPOINT="$API_URL/api/v1/produtos"

if [[ -z "${TOKEN:-}" ]]; then
    if [[ "$API_URL" =~ ^http://(localhost|127\.0\.0\.1)(:|$) ]]; then
        echo "Sem TOKEN: pedindo um ao Keycloak local..."
        TOKEN=$(curl -sf http://localhost:8180/realms/catalogo/protocol/openid-connect/token \
            -d grant_type=client_credentials -d client_id=catalogo-cli -d client_secret=catalogo-cli-segredo \
            | jq -r .access_token) || {
            echo "Não foi possível obter o token. O Keycloak está no ar? (docker compose up -d)" >&2
            exit 1
        }
    else
        echo "Defina TOKEN com um JWT válido para $API_URL (veja \"Publicando a demo\" no README)." >&2
        exit 1
    fi
fi

# SKU | nome | descrição | preço
# Os nomes têm acentos e maiúsculas de propósito: "cafe" encontra "Café Torrado"
# e "CAFETEIRA", e "acucar" encontra "Açúcar Mascavo".
PRODUTOS=$(cat <<'EOF'
CAF-001|Café Torrado em Grãos|Pacote 500g, torra média|29.90
CAF-002|Café Moído Extraforte|Pacote 250g|16.50
CAF-003|CAFETEIRA Italiana|Alumínio, 6 xícaras|89.00
CAF-004|Filtro de Papel para Café|Tamanho 103, caixa com 30|6.90
ACU-001|Açúcar Mascavo|Pacote 1kg|12.40
ACU-002|Açúcar Cristal|Pacote 5kg|24.90
CHA-001|Chá Verde|Caixa com 20 sachês|9.80
CHA-002|Chá de Camomila|Caixa com 15 sachês|8.50
PAO-001|Pão de Queijo Congelado|Pacote 1kg|32.00
LEI-001|Leite Integral|Caixa 1L|5.79
LEI-002|Leite de Aveia|Caixa 1L, sem lactose|14.90
MEL-001|Mel Silvestre|Pote 500g|27.50
BIS-001|Biscoito Amanteigado|Lata 400g|21.90
GRA-001|Granola Tradicional|Pacote 800g|23.70
ACH-001|Achocolatado em Pó|Lata 400g|11.20
XIC-001|Xícara de Porcelana|Com pires, 180ml|34.90
GAR-001|Garrafa Térmica|Inox, 1L|119.90
CAP-001|Cápsula de Café Espresso|Caixa com 10, intensidade 9|22.00
EOF
)

criados=0
existentes=0
falhas=0

while IFS='|' read -r sku nome descricao preco; do
    corpo=$(printf '{"sku":"%s","nome":"%s","descricao":"%s","preco":%s}' "$sku" "$nome" "$descricao" "$preco")
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$ENDPOINT" \
        -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
        --max-time 90 -d "$corpo") || status=000

    case "$status" in
        201) echo "  criado   $sku  $nome"; criados=$((criados + 1)) ;;
        409) echo "  já existe $sku  $nome"; existentes=$((existentes + 1)) ;;
        401) echo "Token recusado (401): expirado, de outro emissor ou sem a audiência catalogo-api." >&2; exit 1 ;;
        000) echo "Sem resposta de $ENDPOINT. A API está no ar?" >&2; exit 1 ;;
        *)   echo "  falhou ($status) $sku  $nome" >&2; falhas=$((falhas + 1)) ;;
    esac
done <<< "$PRODUTOS"

echo
echo "Criados: $criados · Já existiam: $existentes · Falhas: $falhas"
[[ $falhas -eq 0 ]]
