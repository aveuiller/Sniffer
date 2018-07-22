package fr.inria.tandoori.analysis.query.commit;

import fr.inria.tandoori.analysis.model.Commit;
import fr.inria.tandoori.analysis.model.CommitDetails;
import fr.inria.tandoori.analysis.model.GitRename;
import fr.inria.tandoori.analysis.model.Repository;
import fr.inria.tandoori.analysis.persistence.Persistence;
import fr.inria.tandoori.analysis.query.Query;
import fr.inria.tandoori.analysis.query.QueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Actual analysis and persisting of commits and authors for a project.
 */
public class CommitsAnalysis implements Query {
    private static final Logger logger = LoggerFactory.getLogger(CommitsAnalysis.class.getName());
    public static final int BATCH_SIZE = 1000;

    private final int projectId;
    private final Persistence persistence;
    private Repository repository;
    private final Iterator<Map<String, Object>> commits;
    private CommitDetailsChecker detailsChecker;

    public CommitsAnalysis(int projectId, Persistence persistence, Repository repository,
                           Iterator<Map<String, Object>> commits,
                           CommitDetailsChecker detailsChecker) {
        this.projectId = projectId;
        this.persistence = persistence;
        this.repository = repository;
        this.commits = commits;
        this.detailsChecker = detailsChecker;
    }

    @Override
    public void query() throws QueryException {
        List<String> commitStatements = new ArrayList<>();
        List<String> authorStatements = new ArrayList<>();
        List<String> renameStatements = new ArrayList<>();

        int commitCount = 0;
        CommitDetails details;
        Commit paprikaCommit;
        Commit gitCommit;
        while (commits.hasNext()) {
            paprikaCommit = Commit.fromInstance(commits.next());
            try {
                gitCommit = repository.getCommitWithDetails(paprikaCommit.sha);
            } catch (IOException e) {
                throw new QueryException(logger.getName(),
                        "Unable to retrieve commit " + paprikaCommit.sha + " in git repository " + repository);
            }

            logger.debug("[" + projectId + "] => Analyzing commit: " + gitCommit.sha);
            details = detailsChecker.fetch(gitCommit.sha);

            authorStatements.addAll(authorStatements(gitCommit.authorEmail));
            // GitCommit will not contain the right ordinal.
            commitStatements.add(commitStatement(gitCommit, details, paprikaCommit.ordinal));
            renameStatements.addAll(fileRenameStatements(gitCommit, details));

            if (++commitCount % BATCH_SIZE == 0) {
                logger.info("[" + projectId + "] Persist commit batch of size: " + BATCH_SIZE);
                persistBatch(commitStatements, authorStatements, renameStatements);
                authorStatements.clear();
                commitStatements.clear();
                renameStatements.clear();
            }
        }
        persistBatch(commitStatements, authorStatements, renameStatements);
    }

    /**
     * Create Developer and ProjectDeveloper insertion statements.
     *
     * @param emailAddress The developer mail.
     * @return The generated statements.
     */
    private List<String> authorStatements(String emailAddress) {
        String developerQuery = persistence.developerQueryStatement(emailAddress);
        List<String> statements = new ArrayList<>();

        // Try to insert the developer if not exist
        statements.add(persistence.developerInsertStatement(emailAddress));

        // Try to insert the developer/project mapping if not exist
        statements.add(persistence.projectDeveloperInsertStatement(projectId, emailAddress));

        return statements;
    }

    /**
     * Creates the {@link Commit} insertion statement.
     *
     * @param commit  Commit from Git, containing main data (message, author, ...)
     * @param details Commit details containing FileRename and {@link fr.inria.tandoori.analysis.model.GitDiff} info.
     * @param ordinal Commit position in Paprika dataset, since this info is not kept in the Git Commit..
     * @return The generated persistence statement.
     */
    private String commitStatement(Commit commit, CommitDetails details, int ordinal) {
        return persistence.commitInsertionStatement(projectId, commit, details.diff, ordinal);
    }

    /**
     * Creates the FileRename insertion statements.
     *
     * @param commit  The commit to generate renames onto.
     * @param details The commit details containing FileRename.
     * @return The generated statements.
     */
    private List<String> fileRenameStatements(Commit commit, CommitDetails details) {
        List<String> result = new ArrayList<>();

        for (GitRename rename : details.renames) {
            if (!(rename.oldFile.endsWith(".java") && rename.newFile.endsWith(".java"))) {
                continue;
            }

            logger.debug("[" + projectId + "]  => Found .java renamed: " + rename.oldFile);
            logger.trace("[" + projectId + "]    => new file: " + rename.newFile);
            logger.trace("[" + projectId + "]    => Similarity: " + rename.similarity);

            result.add(persistence.fileRenameInsertionStatement(projectId, commit.sha, rename));
        }
        return result;
    }

    /**
     * Persist the current commit state.
     * We add everything in a bulk insert since we must have a coherent state.
     * Warning, we have to insert authors, then commits, then renaming!
     *
     * @param commitStatements CommitEntry to persists.
     * @param authorStatements Developer to persist.
     * @param renameStatements FileRename to persist.
     */
    private void persistBatch(List<String> commitStatements, List<String> authorStatements, List<String> renameStatements) {
        persistence.addStatements(authorStatements.toArray(new String[0]));
        persistence.addStatements(commitStatements.toArray(new String[0]));
        persistence.addStatements(renameStatements.toArray(new String[0]));
        persistence.commit();
    }
}