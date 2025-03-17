package com.articulate.nlp.semconcor;

import com.articulate.nlp.UnitTestBase;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.semconcor.IndexerTest">Terry Norbraten, NPS MOVES</a>
 */
public class IndexerTest extends UnitTestBase {

    Connection conn;
    Path path;

    @Before
    public void startUp() {

        path = Paths.get(System.getenv("CORPORA") + "/FCE.mv.db");
        if (path.toFile().exists())
            path.toFile().delete();
    }

    @After
    public void tearDown() {

        path = null;
        conn = null;
    }

    @Test
    public void testCreateDB() throws Exception {

        System.out.println("------------- IndexerTest.testCreateDB() -------------");
        try {
            conn = DriverManager.getConnection(Indexer.JDBC_STRING, Indexer.UserName, "");
            Indexer.createDB(conn);
            DatabaseMetaData dbm = conn.getMetaData();
            // check if "INDEX" table is there
            try (ResultSet tables = dbm.getTables(null, null, "INDEX", null)) {
                assertTrue(tables.next());
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        assertTrue(path.toFile().exists());
    }

} // end class file IndexerTest.java