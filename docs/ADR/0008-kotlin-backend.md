# ADR-0008 : Backend en Kotlin

## Contexte

Le backend était en Java 21 avec Spring Boot. On souhaite bénéficier de l’expressivité de Kotlin (data classes, nullabilité, concision) tout en gardant la même stack (Spring Boot, JPA, Flyway, mêmes commandes Maven et déploiement).

## Décision

- Le module `backend/` est entièrement en **Kotlin** (`src/main/kotlin`, `src/test/kotlin`).
- **JDK 21** reste la cible JVM ; le build utilise **Maven** avec `kotlin-maven-plugin` (plugins `spring` et `jpa` pour l’interop Spring / JPA), `jackson-module-kotlin`, et `-Xjvm-default=all` pour les méthodes par défaut d’interfaces.
- Les commandes opérationnelles restent **`mvn verify`** (tests + garde-fou JaCoCo), **`mvn spring-boot:run`**, **`mvn package`** ; le Dockerfile continue d’invoquer `mvn package`. Un run rapide sans contrôle de couverture reste possible avec **`mvn test`**.

## Conséquences

- Moins de boilerplate sur les DTO et la logique métier ; conventions du projet (commentaires et messages techniques en anglais) inchangées.
- Les contributeurs installent un JDK 21 et Maven ; l’IDE doit prendre en charge Kotlin.
- Toute évolution de schéma reste via **Flyway** et [DATA_MODEL.md](../DATA_MODEL.md) comme avant.
