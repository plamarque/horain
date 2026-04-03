package com.horain.flyway

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/**
 * H2 names inline column CHECK constraints as CONSTRAINT_N, not PostgreSQL's
 * activity_types_daily_rate_cents_check. V14's DROP IF EXISTS was a no-op; the old CHECK (daily_rate_cents > 0)
 * remained alongside the new one, so inserts with 0 still failed.
 */
@Suppress("unused", "ClassName")
class V15__DropLegacyH2ActivityTypeCheckConstraints : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val conn = context.connection
        if (!conn.metaData.databaseProductName.contains("H2", ignoreCase = true)) {
            return
        }

        val canonicalName = "activity_types_daily_rate_cents_check"
        conn.createStatement().use { st ->
            val checkNames = mutableListOf<String>()
            st.executeQuery(
                """
                SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE (TABLE_NAME = 'ACTIVITY_TYPES' OR TABLE_NAME = 'activity_types')
                  AND CONSTRAINT_TYPE = 'CHECK'
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    checkNames.add(rs.getString(1))
                }
            }

            for (name in checkNames) {
                if (name.equals(canonicalName, ignoreCase = true)) {
                    continue
                }
                st.execute("ALTER TABLE activity_types DROP CONSTRAINT IF EXISTS \"$name\"")
            }

            var hasCanonical = false
            st.executeQuery(
                """
                SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE (TABLE_NAME = 'ACTIVITY_TYPES' OR TABLE_NAME = 'activity_types')
                  AND CONSTRAINT_TYPE = 'CHECK'
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    if (rs.getString(1).equals(canonicalName, ignoreCase = true)) {
                        hasCanonical = true
                        break
                    }
                }
            }

            if (!hasCanonical) {
                st.execute(
                    """
                    ALTER TABLE activity_types ADD CONSTRAINT activity_types_daily_rate_cents_check
                    CHECK (daily_rate_cents >= 0)
                    """.trimIndent()
                )
            }
        }
    }
}
