# CLAUDE.md — App Android do Imagineer

App Android (uso pessoal, instalado por APK) que consome a API do Imagineer.
É também um projeto de aprendizado: Allan quer entender o que está sendo construído e
faz o trabalho por meio do Claude, sem usar o Android Studio. Explique de forma didática,
sem presumir termos técnicos, e diga o "porquê" das escolhas.

## Fonte da verdade
- A especificação vive no repositório da API: `Allandpn/Imagineer`, arquivo `ESPECIFICACAO.md`.
  A Etapa 7 descreve as telas, a Etapa 6 descreve as rotas e o item 7.0 fixa a arquitetura do app.
- O código cita itens dela ("item 7.3a", "incremento 6"). Consulte-a antes de decidir algo de tela ou rota.
- Este repositório NÃO altera a API nem a especificação. Se uma mudança exigir isso, avise e proponha o texto.

## Fluxo de trabalho (mesmo do backend)
1. Especificar o que vai ser feito e por quê.
2. Registrar na especificação (Allan atualiza no repositório da API).
3. Implementar só depois disso.
4. Testar: nenhum item está concluído sem teste.
- Incrementos pequenos e revisáveis, um por item da especificação. Não avançar com testes quebrados.
- Decisão de arquitetura não coberta pela especificação: perguntar antes.
- Se a implementação divergir do plano, registrar a divergência e o motivo.

## Stack (decidida no item 7.0 — não trocar sem pedido)
- Kotlin + Jetpack Compose, Material 3 padrão (sem tema customizado). Sem XML de layout.
- MVVM: `telas/<tela>/…ViewModel.kt` + `Tela….kt`; estado como `sealed interface` em `StateFlow`.
- Injeção de dependência MANUAL em `ImagineerApp` (sem Hilt).
- Rede em `rede/`: Retrofit + OkHttp + kotlinx-serialization. Repositório = interface + `…PeloRetrofit`.
  Falhas passam por `chamarApi` → `ResultadoDaChamada`; o ViewModel nunca vê exceção de rede.
- Persistência: DataStore (Preferences) em `dados/`. Sem Room e sem cache de livros (o servidor é a fonte).
- Navegação: Navigation Compose 2.9 com rotas `@Serializable`. Não migrar para Navigation 3.
- Módulo único `:app`. Sem login: a proteção é a rede (Tailscale). O HTTP em texto puro é deliberado;
  reavaliar só se o app for distribuído a outras pessoas.
- Versões: AGP 9.4.1, Kotlin 2.2.10, Gradle 9.6.0, minSdk 31, targetSdk/compileSdk 37.
  Dependências só via `gradle/libs.versions.toml`.

## Convenções
- Tudo em português: identificadores, comentários, textos de tela e commits.
  Termos técnicos já naturalizados (prompt, backend, endpoint) ficam. Sem tradução natural: perguntar.
- DTOs espelham o JSON da API em snake_case, com `@Suppress("PropertyName")`, sem `@SerialName`.
- "Arquivado" é só o vocabulário da tela para o campo `ignorado` da API.
- Toda dependência de rede ou dados nova ganha interface, para testar com um falso.
- Mensagens de erro para o usuário ficam escritas na camada que as cria.
- Telas ainda provisórias (`TelaProvisoria`): Frame, Prompt, Elementos, Perfis.

## Testes
- Unitários em `app/src/test`, espelhando os pacotes (JUnit4, coroutines-test, MockWebServer).
- Todo ViewModel novo ou alterado precisa de teste. `androidTest` ainda só tem o exemplo padrão.
- Comando: `./gradlew testDebugUnitTest` (nome padrão do Gradle; ainda NÃO verificado neste projeto —
  o ambiente na nuvem pode não ter o Android SDK. Se falhar, dizer o motivo em vez de dar o teste por passado).

## Segredos e segurança
- Nunca ler, exibir nem commitar: `local.properties`, keystores, chaves de API.
- A keystore de assinatura fica fora do repositório. Perdê-la obriga a desinstalar o app.
- Futuro (item 7.0): a chave da OpenRouter ficará em armazenamento criptografado no celular e irá no
  header `X-Chave-API-OpenRouter`. O servidor não a guarda. Nunca logar esse valor.

## Git
- Commits pequenos, com o motivo da mudança, um por item da especificação.

## Skills e agentes do projeto (.claude/)
- `compose-expert` (aldefy/compose-skill v2.4.0, MIT, copiada em 2026-09-30, sem atualização automática):
  telas Compose. As regras acima têm precedência sobre qualquer sugestão dela
  (em especial: não migrar para Navigation 3).
- `code-reviewer` e `codebase-onboarding-engineer` (msitarzewski/agency-agents, MIT, copiados em 2026-09-30):
  só leitura (`tools: Read, Grep, Glob`). Não têm conhecimento do projeto além deste arquivo.
- Revisão: telas Compose → compose-expert; o resto → code-reviewer.
- Não instalar skills, agentes ou scripts de terceiros sem aprovação explícita.
