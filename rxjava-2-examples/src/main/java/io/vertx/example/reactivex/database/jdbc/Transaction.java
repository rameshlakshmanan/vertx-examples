package io.vertx.example.reactivex.database.jdbc;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.example.util.Runner;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.jdbc.JDBCClient;

/*
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */
public class Transaction extends AbstractVerticle {

  // Convenience method so you can run it in your IDE
  public static void main(String[] args) {
    Runner.runExample(Transaction.class);
  }

  @Override
  public void start() throws Exception {

    JsonObject config = new JsonObject().put("url", "jdbc:hsqldb:mem:test?shutdown=true")
      .put("driver_class", "org.hsqldb.jdbcDriver");

    String sql = "CREATE TABLE colors (" +
      "id INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1, INCREMENT BY 1) PRIMARY KEY, " +
      "name VARCHAR(255), " +
      "datetime TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL)";

    JDBCClient client = JDBCClient.createShared(vertx, config);

    // Connect to the database
    client
      .rxGetConnection()
      .flatMap(conn ->
        conn
          // disable auto commit to manage transaction manually
          .rxSetAutoCommit(false)
          // switch from Completable to default Single value
          .toSingleDefault(false)
          .flatMap(autoCommit -> conn.rxExecute(sql).toSingleDefault(true))
          .flatMap(executed -> conn.rxUpdateWithParams("INSERT INTO colors (name) VALUES (?)", new JsonArray().add("BLACK")))
          .flatMap(updateResult -> conn.rxUpdateWithParams("INSERT INTO colors (name) VALUES (?)", new JsonArray().add("WHITE")))
          .flatMap(updateResult -> conn.rxUpdateWithParams("INSERT INTO colors (name) VALUES (?)", new JsonArray().add("PURPLE")))
          .flatMap(updateResult -> conn.rxQuery("SELECT * FROM colors"))
          // commit if all succeeded
          .doOnSuccess(resultSet -> conn.rxCommit().subscribe())
          // rollback if any failed
          .doOnError(throwable -> conn.rxRollback().subscribe())
          // close the connection regardless succeeded or failed
          .doAfterTerminate(conn::close)
      ).subscribe(resultSet -> {
      // Subscribe to get the final result
      System.out.println("Results : " + resultSet.getRows());
    }, Throwable::printStackTrace);
  }
}
