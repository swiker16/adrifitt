package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * A client can have many subscriptions (history) but only one current one ({@code active = true}).
 * PostgreSQL enforces it with a partial unique index; H2 (tests/local) has no partial indexes,
 * so there the rule is only enforced by {@code SubscriptionService}.
 */
public class V3__one_current_subscription_per_client extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String product = context.getConnection().getMetaData().getDatabaseProductName();
        if (!"PostgreSQL".equalsIgnoreCase(product)) {
            return;
        }
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_subscriptions_one_current "
                    + "ON subscriptions (client_id) WHERE active");
        }
    }
}
