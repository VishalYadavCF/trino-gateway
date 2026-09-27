/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway;

import com.google.common.io.Resources;
import io.airlift.log.Logger;
import io.airlift.log.Logging;
import io.trino.gateway.ha.HaGatewayLauncher;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.FlywayMigration;
import org.jdbi.v3.core.Jdbi;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.trino.TrinoContainer;

import java.io.IOException;
import java.util.List;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

public final class TrinoGatewayRunner
{
    private TrinoGatewayRunner() {}

    public static void main(String[] args)
            throws Exception
    {
        Logging.initialize();
        Logger log = Logger.get(TrinoGatewayRunner.class);

        TrinoContainer trino1 = new TrinoContainer("trinodb/trino:466");
        trino1.setPortBindings(List.of("8081:8080"));
        trino1.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino1.start();
        TrinoContainer trino2 = new TrinoContainer("trinodb/trino:466");
        trino2.setPortBindings(List.of("8082:8080"));
        trino2.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino2.start();

        PostgreSQLContainer postgres = createPostgreSqlContainer();
        postgres.withUsername("trino_gateway_db_admin");
        postgres.withPassword("P0stG&es");
        postgres.withDatabaseName("trino_gateway_db");
        postgres.setPortBindings(List.of("5432:5432"));
        postgres.start();
        migrateAndSeed(postgres, "add_backends_postgres.sql");

        MySQLContainer mysql = new MySQLContainer("mysql:5.7");
        mysql.withUsername("root");
        mysql.withPassword("root123");
        mysql.withDatabaseName("trinogateway");
        mysql.setPortBindings(List.of("3306:3306"));
        mysql.start();
        migrateAndSeed(mysql, "add_backends_mysql.sql");

        OpenTracingCollector tracingCollector = new OpenTracingCollector();
        tracingCollector.start();

        HaGatewayLauncher.main(new String[] {"gateway-ha/config.yaml"});

        log.info("======== SERVER STARTED ========");
        log.info("Tracing: http://localhost:16686");
    }

    /**
     * Creates the schema with the same Flyway migrations the gateway runs on startup,
     * then seeds the sample backends. Seed data can only be inserted once the tables
     * exist, which is why this is not done through the container's init scripts.
     */
    private static void migrateAndSeed(JdbcDatabaseContainer<?> container, String seedResource)
            throws IOException
    {
        DataStoreConfiguration config = new DataStoreConfiguration(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                container.getDriverClassName(),
                true,
                4,
                true);
        FlywayMigration.migrate(config);

        String seedSql = Resources.toString(Resources.getResource(seedResource), UTF_8);
        Jdbi.create(config.getJdbcUrl(), config.getUser(), config.getPassword())
                .useHandle(handle -> handle.createScript(seedSql).execute());
    }
}
