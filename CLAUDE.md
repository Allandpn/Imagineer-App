# CLAUDE.md — App Android do Imagineer

App Android (uso pessoal, instalado por APK) que consome a API do Imagineer.
É também um projeto de aprendizado: Allan quer entender o que está sendo construído e
faz o trabalho por meio do Claude e usa o Android Studio só para rodar o app no tablet de teste
(Galaxy Tab S8, por USB) e conferir o resultado. Explique de forma didática,
sem presumir termos técnicos, e diga o "porquê" das escolhas.

## Fonte da verdade
- A especificação vive no repositório da API: `Allandpn/Imagineer`, arquivo `ESPECIFICACAO.md`.
  A Etapa 7 descreve as telas, a Etapa 6 descreve as rotas e o item 7.0 fixa a arquitetura do app.
- O código cita itens dela ("item 7.3a", "incremento 6"). Consulte-a antes de decidir algo de tela ou rota.
- **Código da API: nunca é alterado a partir daqui.** A **especificação** pode ser: quando o app precisa de uma
  mudança de rota, campo ou tela, escreva-a em `ESPECIFICACAO.md` seguindo as regras de convivência do
  `CLAUDE.md` do repositório da API (que é a fonte dessas regras — não repetir aqui).

## Fluxo de trabalho (mesmo do backend)
1. Especificar o que vai ser feito e por quê.
2. Registrar na especificação (no repositório da API, pelas regras de convivência de lá).
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
- Persistência: DataStore (Preferences) em `dados/` só para o endereço do servidor. **Room + KSP** guardam o
  índice do que há no aparelho e **arquivos** guardam o texto (e, nos próximos passos, as imagens), tudo em
  `noBackupFilesDir` e em `local/` (item 7.0a, passo 1, implementado). O servidor continua sendo a fonte da
  verdade; offline é só de leitura; a cópia local é descartável (banco de formato novo = apagar e refazer, sem
  migração). Falha local nunca impede a leitura (`melhorEsforco`). Interface + versão em memória para testar
  (`IndiceLocal`, `ArmazemDeTextos`). Imagens (passo 3) e "Baixar para ler offline" (passo 4) ainda não existem:
  não adicionar cache de imagem por conta própria.
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
- Comando: `./gradlew testDebugUnitTest`. Verificado em 30/09/2026 numa cópia limpa do repositório, com o
  Android SDK instalado: 243 testes, cerca de 1,5 minuto. Num ambiente sem o SDK (a nuvem, por exemplo) o
  comando pode falhar; nesse caso, dizer o motivo em vez de dar o teste por passado.

## Segredos e segurança
- Nunca ler, exibir nem commitar: `local.properties`, keystores, chaves de API.
- A keystore de assinatura fica fora do repositório. Perdê-la obriga a desinstalar o app.
- Futuro (item 7.0): a chave da OpenRouter ficará em armazenamento criptografado no celular e irá no
  header `X-Chave-API-OpenRouter`. O servidor não a guarda. Nunca logar esse valor.

## Git
- Commits pequenos, com o motivo da mudança, um por item da especificação.
- **Sessão remota (nuvem), sem o Allan presente:** branch curta e pull request; quem mescla é ele.
  **Sessão local com o Allan presente:** commit e push direto na `main`, só quando ele pedir explicitamente
  (ele valida no tablet antes).

## Skills e agentes do projeto (.claude/)
- `compose-expert` (aldefy/compose-skill v2.4.0, MIT, copiada em 2026-09-30, sem atualização automática):
  telas Compose. As regras acima têm precedência sobre qualquer sugestão dela
  (em especial: não migrar para Navigation 3). A pasta `references/source-code/` (código-fonte de
  bibliotecas, ~2 MB) foi removida: é consultável sem precisar morar no repositório. Há uma nota local
  na `SKILL.md` avisando isso (única edição feita no material de terceiros).
- `code-reviewer` (msitarzewski/agency-agents, MIT, copiado em 2026-09-30): só leitura
  (`tools: Read, Grep, Glob`). Não tem conhecimento do projeto além deste arquivo.
  O agente `codebase-onboarding-engineer` foi removido: este arquivo e a especificação já cumprem esse papel.
- **Aprovado pelo Allan em 30/09/2026**, depois de uma varredura (só markdown e licenças; sem scripts, hooks
  nem instruções de rede ou segredos).
- Revisão: telas Compose → compose-expert; o resto → code-reviewer.
- Não instalar skills, agentes ou scripts de terceiros sem aprovação explícita.
