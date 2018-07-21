package fr.inria.tandoori.analysis;

import fr.inria.tandoori.analysis.persistence.Persistence;
import fr.inria.tandoori.analysis.persistence.PostgresqlPersistence;
import fr.inria.tandoori.analysis.query.branch.BranchQuery;
import fr.inria.tandoori.analysis.query.commit.CommitsQuery;
import fr.inria.tandoori.analysis.query.Query;
import fr.inria.tandoori.analysis.query.QueryException;
import fr.inria.tandoori.analysis.model.Repository;
import fr.inria.tandoori.analysis.query.smell.SmellQuery;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fr.inria.tandoori.analysis.Main.DATABASE_PASSWORD;
import static fr.inria.tandoori.analysis.Main.DATABASE_URL;
import static fr.inria.tandoori.analysis.Main.DATABASE_USERNAME;

/**
 * Class handling a single app analysis process in Tandoori.
 */
public class SingleAppAnalysis {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SingleAppAnalysis.class.getName());

    private final String appName;
    private final String appRepo;
    private final String paprikaDB;
    private final String githubToken;
    private final String projectUrl;

    private List<Query> getAnalysisProcess(int appId, Repository repository, Persistence persistence) {
        List<Query> analysisProcess = new ArrayList<>();

        analysisProcess.add(new CommitsQuery(appId, paprikaDB, repository, persistence));
        analysisProcess.add(new BranchQuery(appId, repository, persistence));
        analysisProcess.add(new SmellQuery(appId, paprikaDB, persistence));

        // if (githubToken != null) {
        //     analysisProcess.add(new DevelopersQuery(appRepo, githubToken));
        // }
        return analysisProcess;
    }

    /**
     * Compute a single project analysis.
     *
     * @param appName     Name of the application under analysis.
     * @param appRepo     Github repository as "username/repository" or local path.
     * @param paprikaDB   Path to paprika database.
     * @param githubToken Github API token to query on developers.
     */
    SingleAppAnalysis(String appName, String appRepo, String paprikaDB, String githubToken, String url) {
        this.appName = appName;
        this.appRepo = appRepo;
        this.paprikaDB = paprikaDB;
        this.githubToken = githubToken;
        projectUrl = url;
    }

    /**
     * Creates a new project in the persistence if not already existing,
     * <p>
     * then always fetch and return the project ID.
     *
     * @param appName     The project to persist.
     * @param url         The project URL.
     * @param persistence The persistence to use.
     * @return The project identifier in the database.
     */
    private static int persistApp(String appName, String url, Persistence persistence) {
        String projectInsert = "INSERT INTO Project (name, url) VALUES ('" + appName + "', '" + url + "') ON CONFLICT DO NOTHING;";
        persistence.addStatements(projectInsert);
        persistence.commit();

        String idQuery = persistence.projectQueryStatement(appName);
        List<Map<String, Object>> result = persistence.query(idQuery + ";");
        // TODO: Maybe be less violent / test the returned data
        return (int) result.get(0).get("id");
    }

    public void analyze() throws AnalysisException {
        // Persistence persistence = new SQLitePersistence("output.sqlite");
        Persistence persistence = new PostgresqlPersistence(DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD);
        this.analyze(persistence);
    }

    public void analyze(Persistence persistence) throws AnalysisException {
        persistence.initialize();
        int appId = persistApp(appName, projectUrl, persistence);

        Repository repository = new Repository(appRepo);
        try {
            repository.initializeRepository();
        } catch (Repository.RepositoryException e) {
            throw new AnalysisException("Unable to open repository", e);
        }
        logger.info("[" + appId + "] Analyzing application: " + appName);
        for (Query process : getAnalysisProcess(appId, repository, persistence)) {
            try {
                process.query();
            } catch (QueryException e) {
                logger.warn("An error occurred during query!", e);
            }
        }

        repository.finalizeRepository();

        logger.info("[" + appId + "] Analysis done for: " + appName);
        persistence.close();
    }

    /**
     * Constructor for command line arguments
     *
     * @param arguments The command line arguments.
     */
    SingleAppAnalysis(Namespace arguments) {
        this(
                arguments.getString("name"),
                arguments.getString("repository"),
                arguments.getString("database"),
                arguments.getString("githubToken"),
                arguments.getString("url")
        );
    }

    /**
     * Defines the available inputs for a single app analysis.
     *
     * @param parser The parser to configure
     */
    static void setArguments(Subparser parser) {
        parser.addArgument("-n", "--name")
                .help("Application name")
                .type(String.class)
                .required(true);

        parser.addArgument("-r", "--repository")
                .help("Github repository as \"username/repository\" or local path")
                .type(String.class)
                .required(true);

        parser.addArgument("-db", "--database")
                .help("Path to Paprika database")
                .type(String.class)
                .required(true);

        parser.addArgument("-k", "--githubToken")
                .help("Github API token to query on developers")
                .type(String.class)
                .required(false);

        parser.addArgument("-u", "--url")
                .help("Repository complete path to log in database")
                .type(String.class)
                .required(false);
    }
}
