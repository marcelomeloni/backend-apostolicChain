# ⛪ Apostolic Chain

> Um registro imutável da sucessão apostólica católica — de Jesus Cristo até os dias atuais — ancorado na blockchain Solana, servido por uma API Java/Spring Boot e visualizado como um grafo interativo de força.

---

## 📖 O que é este projeto

A sucessão apostólica da Igreja Católica é uma cadeia ininterrupta de consagrações episcopais rastreável, em princípio, de qualquer bispo vivo até São Pedro e Jesus Cristo. Este projeto codifica essa cadeia **on-chain na Solana**, tornando cada elo da sucessão **criptograficamente verificável, imutável e publicamente auditável**.

Cada registro de clérigo é uma **Program Derived Account (PDA)** na Solana. Cada conta armazena um `hash`, um `parent_hash` apontando para o bispo consagrante, nome, papel e datas. A cadeia pode ser percorrida de qualquer nó até a gênese — a instrução `initialize_genesis` que criou Jesus e Pedro como contas raiz.

Um backend Spring Boot espelha esses dados no PostgreSQL para consultas rápidas e expõe uma API REST. Um frontend React renderiza toda a sucessão como um grafo de força vivo e interativo.

---

## 🏛️ Arquitetura Geral
```
┌──────────────────────────────────────────────────────────┐
│                      SOLANA DEVNET                        │
│                                                           │
│  Programa : apostolic_chain (Anchor / Rust)               │
│  Program ID: HKUdr1NeewdqE3vEzHmAu9waow5p4bHg6V6t4iM5cLhK│
│                                                           │
│  PDAs     : [b"clergy" + seed_bytes (32 bytes)]           │
│  Cadeia   : Jesus → Pedro → Bispo → ... → Papa           │
└────────────────────────┬─────────────────────────────────┘
                         │  espelhado via SolanaConfig.java
┌────────────────────────▼─────────────────────────────────┐
│               SPRING BOOT API  (Java 17)                  │
│                                                           │
│  Autenticação JWT           →  AuthController             │
│  Gestão de clérigos (admin) →  ClergyController           │
│  Leitura pública            →  PublicClergyController     │
│  Analytics                  →  PublicStatsController      │
│  Persistência               →  PostgreSQL + Spring JPA    │
│  Ancoragem Solana           →  SolanaConfig + IDL JSON    │
└────────────────────────┬─────────────────────────────────┘
                         │  REST / JSON
┌────────────────────────▼─────────────────────────────────┐
│               FRONTEND REACT  (Vite)                      │
│                                                           │
│  Visualização do grafo  →  react-force-graph-2d + d3      │
│  Renderização de nós    →  Canvas API customizado         │
│  Traçado de linhagem    →  Cadeia resolvida pelo backend   │
│  Painel administrativo  →  CRUD completo de clérigos      │
│  Timeline histórica     →  Navegação por século           │
└──────────────────────────────────────────────────────────┘
```

---

## ⛓️ Como a Blockchain funciona aqui

### Estrutura da Conta on-chain
```rust
// programs/apostolic_chain/src/state.rs

#[account]
pub struct Clergy {
    pub hash: String,                    // Identificador único (0x + 64 hex)
    pub parent_hash: String,             // Hash do bispo consagrante
    pub name: String,                    // Nome do clérigo
    pub role: Role,                      // Bishop | Pope | Root
    pub start_date: i64,                 // Data de ordenação (Unix timestamp)
    pub papacy_start_date: Option<i64>,  // Início do papado (somente papas)
    pub bump: u8,                        // Bump do PDA
}

pub enum Role {
    Bishop,
    Pope,
    Root,
}
```

### Instruções do Programa

| Instrução | Descrição |
|---|---|
| `initialize_genesis` | Cria as contas raiz de Jesus Cristo e São Pedro, estabelecendo o bloco gênese da cadeia |
| `create_clergy` | Cria um novo clérigo, validando na própria Solana que o `parent_hash` aponta para uma conta existente e íntegra |

### Verificação de Linhagem on-chain
```rust
// programs/apostolic_chain/src/instructions/create_clergy.rs

if parent_hash == "00x00x00" {
    // Linhagem histórica quebrada — sentinela aceito
    msg!("Registro com linhagem quebrada (Dados Perdidos). Aceito.");
} else {
    // O runtime da Solana verifica que a conta pai existe
    require!(
        ctx.accounts.parent_account.is_some(),
        ErrorCode::ParentAccountMissing
    );
    let parent = ctx.accounts.parent_account.as_ref().unwrap();

    // E que o hash bate exatamente
    require!(parent.hash == parent_hash, ErrorCode::InvalidParentHash);

    msg!("Linhagem verificada: {} -> {}", parent.name, name);
}
```

> Nenhum registro falso pode ser inserido. A cadeia é verificada pelo runtime da Solana no momento da escrita — não pela aplicação.

---

## 🗂️ Estrutura do Projeto

### Backend — Spring Boot
```
src/main/java/com/example/demo/
│
├── config/
│   ├── AsyncConfig.java              # Pool de threads para operações assíncronas
│   ├── JwtAuthenticationFilter.java  # Filtro JWT executado por requisição
│   ├── JwtUtil.java                  # Geração e validação de tokens JWT
│   ├── SecurityConfig.java           # Regras de segurança, CORS e rotas públicas
│   └── SolanaConfig.java             # Cliente RPC + integração com programa Anchor
│
├── controller/
│   ├── AuthController.java           # POST /api/auth/login
│   ├── ClergyController.java         # CRUD admin (requer JWT)
│   ├── PublicClergyController.java   # GET /api/public/clergy/**
│   └── PublicStatsController.java    # GET /api/public/stats/**
│
├── dto/
│   ├── ClergyDTO.java                # Payload de criação/edição
│   ├── DashboardStatsDTO.java        # Estatísticas do painel admin
│   ├── GenesisDTO.java               # Parâmetros de inicialização da gênese
│   ├── LoginDTO.java                 # Credenciais de login
│   └── PublicStatsDTO.java           # Estatísticas públicas
│
├── model/
│   ├── Admin.java                    # Entidade de administrador
│   ├── Clergy.java                   # Entidade principal de clérigo
│   ├── DailyVisit.java               # Registro de visitas diárias
│   └── SiteAnalytics.java            # Métricas do site
│
├── repository/
│   ├── ClergyRepository.java         # Queries JPA para clérigos (admin)
│   ├── PublicClergyRepository.java   # Queries públicas + SQL recursivo WITH RECURSIVE
│   ├── AdminRepository.java          # Busca de administradores por e-mail
│   ├── DailyVisitRepository.java     # Persistência de visitas
│   └── SiteAnalyticsRepository.java  # Persistência de analytics
│
├── service/
│   ├── ClergyService.java            # Lógica de negócio + ancoragem na Solana
│   ├── PublicClergyService.java      # Consultas públicas e traçado de linhagem
│   └── AnalyticsService.java         # Tracking de visitas e métricas
│
└── util/
    └── AnchorDiscriminator.java      # Cálculo de discriminadores Anchor (SHA-256)

src/main/resources/
└── idl/
    └── apostolic_chain.json          # IDL gerado pelo Anchor (ABI do programa Solana)
```

### Frontend — React + Vite
```
src/
│
├── components/
│   ├── Graph/
│   │   ├── constants.js    # Raios, cores, thresholds de zoom e espaçamento
│   │   ├── physics.js      # d3-force: força Y ordinal, X lateral, colisão, carga
│   │   ├── renderers.js    # Canvas API: nós com foto, halos dourados, labels, links
│   │   └── utils.js        # Numerais romanos, helpers de texto e desenho
│   │
│   ├── admin/
│   │   ├── CreateBishop.jsx  # Formulário de criação de clérigo
│   │   ├── Dashboard.jsx     # Painel com estatísticas e métricas
│   │   ├── DataLists.jsx     # Listagem e busca de clérigos
│   │   ├── Header.jsx        # Cabeçalho do painel admin
│   │   └── Sidebar.jsx       # Navegação lateral admin
│   │
│   ├── ControlPanel.jsx    # Barra de busca e controles do grafo
│   ├── GraphCanvas.jsx     # ForceGraph2D com física customizada e câmera
│   ├── HUD.jsx             # Heads-up display com informações do nó selecionado
│   ├── Legend.jsx          # Legenda visual de tipos de nó e link
│   └── Timeline.jsx        # Navegação histórica por século (I ao XXI)
│
├── contexts/
│   └── AuthContext.jsx     # Contexto global de autenticação JWT
│
├── pages/
│   ├── ApostolicTree.jsx   # Página principal: grafo + lógica de traçado
│   ├── Admin.jsx           # Área administrativa protegida
│   ├── LandingPage.jsx     # Página inicial pública
│   └── Login.jsx           # Tela de login do administrador
│
└── services/
    ├── ApiService.js       # Cliente HTTP base com injeção automática de JWT
    ├── HomeService.js      # Endpoints públicos: cadeia, busca, traçado
    ├── AdminService.js     # Endpoints administrativos autenticados
    └── LoginService.js     # Autenticação e gestão de token
```

---

## 🔍 Funcionalidade Central: Traçado de Linhagem

Ao clicar em qualquer papa no grafo, o sistema traça o caminho completo de consagração até Jesus Cristo:
```
1. Usuário clica em Bento XVI no grafo
         ↓
2. GET /api/public/clergy/chain/{hash}
         ↓
3. Backend executa query recursiva no PostgreSQL:

   WITH RECURSIVE lineage AS (
       SELECT * FROM clergy WHERE hash = :startHash

       UNION ALL

       SELECT c.* FROM clergy c
       INNER JOIN lineage l ON l.parent_hash = c.hash
       WHERE l.depth < 150
         AND l.parent_hash IS NOT NULL
         AND l.parent_hash NOT IN ('00x00x00', '00X00X00')
   )
   SELECT * FROM lineage ORDER BY depth ASC

         ↓
4. Retorna array ordenado: [Bento XVI, Cardeal X, Bispo Y, ..., São Pedro]
         ↓
5. Frontend adiciona nós e links ao grafo de uma só vez (sem loop de setState)
         ↓
6. Partículas douradas animadas percorrem o caminho destacado
```

Quando a linhagem histórica está incompleta (`parent_hash = "00x00x00"`), o sistema exibe um nó **"Dados Perdidos"** conectando ao ponto mais próximo conhecido, preservando a integridade visual da cadeia.

---

## 🚀 Como Rodar

### Pré-requisitos

| Ferramenta | Versão mínima |
|---|---|
| Java | 17+ |
| Node.js | 18+ |
| PostgreSQL | 14+ |
| Solana CLI | 1.18+ |
| Anchor CLI | 0.29+ |

### 1. Backend
```bash
# Clone e configure
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties

# Edite application.properties com suas credenciais:
# spring.datasource.url=jdbc:postgresql://localhost:5432/apostolic
# spring.datasource.username=postgres
# spring.datasource.password=...
# jwt.secret=sua-chave-secreta-aqui
# solana.rpc.url=https://api.devnet.solana.com
# solana.program.id=HKUdr1NeewdqE3vEzHmAu9waow5p4bHg6V6t4iM5cLhK

# Rodar
./mvnw spring-boot:run

# API disponível em: http://localhost:8080
```

### 2. Frontend
```bash
cd meu-projeto-react

npm install

npm run dev

# App disponível em: http://localhost:5173
```

### 3. Programa Solana _(já deployado em Devnet — opcional)_
```bash
cd programs/apostolic_chain

# Build
anchor build

# Deploy
anchor deploy --provider.cluster devnet

# Inicializar a gênese (executar apenas uma vez)
anchor run initialize
```

---

## 🔐 Segurança e Autenticação

| Rota | Acesso |
|---|---|
| `GET /api/public/**` | Público — sem autenticação |
| `POST /api/auth/login` | Público — retorna Bearer JWT |
| `POST /api/clergy/**` | 🔒 Requer Bearer JWT |
| `PUT /api/clergy/**` | 🔒 Requer Bearer JWT |
| `DELETE /api/clergy/**` | 🔒 Requer Bearer JWT |

O filtro `JwtAuthenticationFilter` ignora automaticamente rotas públicas via `shouldNotFilter`, evitando que a cadeia de segurança do Spring rejeite requisições sem token antes mesmo de chegar ao controller.

---

## 🗄️ Modelo de Dados
```sql
CREATE TABLE public.clergy (
    hash              VARCHAR(66)  PRIMARY KEY,   -- "0x" + 64 hex chars
    parent_hash       VARCHAR(66),                -- Hash do bispo consagrante
    name              VARCHAR(255) NOT NULL,
    role              VARCHAR(10)  DEFAULT 'BISHOP', -- BISHOP | POPE | ROOT
    start_date        DATE,                       -- Data de ordenação episcopal
    papacy_start_date DATE,                       -- Início do papado (papas)
    created_at        TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX idx_clergy_parent ON clergy(parent_hash);
CREATE INDEX idx_clergy_name   ON clergy(name);
CREATE INDEX idx_clergy_role   ON clergy(role);
```

---

## 🛠️ Stack Técnica

| Camada | Tecnologia |
|---|---|
| **Blockchain** | Solana Devnet + Anchor Framework (Rust) |
| **Backend** | Java 17, Spring Boot 3, Spring Security, JPA/Hibernate |
| **Banco de dados** | PostgreSQL — queries recursivas `WITH RECURSIVE` |
| **Frontend** | React 18, Vite, Tailwind CSS |
| **Grafo** | react-force-graph-2d, d3-force |
| **Renderização** | Canvas API — nós customizados com fotos, halos e labels |
| **Autenticação** | JWT stateless (sem sessão no servidor) |
| **Animação** | Partículas direcionais ao longo do caminho apostólico |

---

## 📜 Licença

Uso restrito. Os dados históricos de sucessão apostólica são de domínio público. Os registros on-chain são imutáveis por design do protocolo Solana.

---

> _"Tu és Pedro, e sobre esta pedra edificarei a minha Igreja."_
> — Mateus 16:18
