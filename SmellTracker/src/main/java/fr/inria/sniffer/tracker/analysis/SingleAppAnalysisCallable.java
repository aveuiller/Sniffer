package fr.inria.sniffer.tracker.analysis;

import fr.inria.sniffer.tracker.analysis.persistence.queries.DeveloperQueries;
import fr.inria.sniffer.tracker.analysis.persistence.queries.JDBCBranchQueries;
import fr.inria.sniffer.tracker.analysis.persistence.queries.JDBCCommitQueries;
import fr.inria.sniffer.tracker.analysis.persistence.queries.JDBCProjectQueries;
import fr.inria.sniffer.tracker.analysis.persistence.queries.SmellQueries;
import fr.inria.sniffer.tracker.analysis.persistence.PostgresqlPersistence;
import fr.inria.sniffer.tracker.analysis.persistence.queries.BranchQueries;
import fr.inria.sniffer.tracker.analysis.persistence.queries.CommitQueries;
import fr.inria.sniffer.tracker.analysis.persistence.queries.JDBCDeveloperQueries;
import fr.inria.sniffer.tracker.analysis.persistence.queries.JDBCSmellQueries;
import fr.inria.sniffer.tracker.analysis.persistence.queries.ProjectQueries;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.Callable;

final class SingleAppAnalysisCallable implements Callable<Void> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SingleAppAnalysisCallable.class.getName());
    private String application;
    private String repository;
    private String paprikaDB;
    private String githubToken;
    private String url;
    DataSource connections;

    public SingleAppAnalysisCallable(String application, String repository, String paprikaDB,
                                     String githubToken, String url, DataSource connections) {
        this.application = application;
        this.repository = repository;
        this.paprikaDB = paprikaDB;
        this.githubToken = githubToken;
        // Set null if no url, else join the GITHUB_URL with the given 'owner/project' path.
        if (url == null) {
            this.url = null;
        } else {
            url = url.trim();
            this.url = Main.GITHUB_URL + (url.startsWith("/") ? url.substring(1) : url);
        }
        this.connections = connections;
    }

    @Override
    public Void call() throws Exception {
        SingleAppAnalysis analysis = new SingleAppAnalysis(application, repository, paprikaDB, githubToken, url);
        PostgresqlPersistence persistence = new PostgresqlPersistence(connections.getConnection());
        ProjectQueries projectQueries = new JDBCProjectQueries();
        DeveloperQueries developerQueries = new JDBCDeveloperQueries();
        CommitQueries commitQueries = new JDBCCommitQueries(developerQueries);
        SmellQueries smellQueries = new JDBCSmellQueries(commitQueries);
        BranchQueries branchQueries = new JDBCBranchQueries(commitQueries, smellQueries);
        try {
            analysis.analyze(persistence, projectQueries, developerQueries, commitQueries, smellQueries, branchQueries);
        } catch (AnalysisException e) {
            logger.error("Unable to perform analysis on project " + application, e);
        }
        return null;
    }

    @Override
    public String toString() {
        return "SingleAnalysisTask{" +
                "application='" + application + '\'' +
                ", repository='" + repository + '\'' +
                ", paprikaDB='" + paprikaDB + '\'' +
                ", url='" + url + '\'' +
                ", githubToken='" + (githubToken == null ? null : "XXXX (is set)") + '\'' +
                '}';
    }
}
