package fr.inria.tandoori.analysis.query;

import fr.inria.tandoori.analysis.FilesUtils;
import fr.inria.tandoori.analysis.persistence.Persistence;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetch all commits and developers for a project.
 */
public class CommitsQuery implements Query {
    private static final Logger logger = LoggerFactory.getLogger(CommitsQuery.class.getName());
    private int projectId;
    private String repository;
    private Persistence persistence;
    private final Path cloneDir;

    private static final int SIMILARITY_THRESHOLD = 50;

    public CommitsQuery(int projectId, String repository, Persistence persistence) {
        this.projectId = projectId;
        this.repository = repository;
        this.persistence = persistence;
        try {
            this.cloneDir = Files.createTempDirectory("tandoori");
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temporary directory", e);
        }
    }

    @Override
    public void query() throws QueryException {
        logger.info("### Starting Commits insertion ###");
        Git gitRepo = initializeRepository();
        Iterable<RevCommit> commits = getCommits(gitRepo);
        List<RevCommit> commitsList = new ArrayList<>();
        // Reverse our commit list.
        for (RevCommit commit : commits) {
            commitsList.add(0, commit);
        }

        String[] statements = new String[commitsList.size()];
        int commitCount = 0;
        for (RevCommit commit : commitsList) {
            statements[commitCount] = commitPersistStatement(commit, commitCount++);
            // TODO: Handle multiple transactions ?
            insertFileModification(commit);
        }
        // This is better to add them together as it creates a batch insert.
        persistence.addStatements(statements);
        persistence.commit();

        finalizeRepository();
    }

    private static Iterable<RevCommit> getCommits(Git gitRepo) throws QueryException {
        Iterable<RevCommit> commits;
        try {
            commits = gitRepo.log().call();
        } catch (GitAPIException e) {
            throw new QueryException(logger.getName(), e);
        }
        return commits;
    }

    private String commitPersistStatement(RevCommit commit, int count) {
        int authorId = insertAuthor(commit.getAuthorIdent().getEmailAddress());
        GitDiffResult diff = GitDiffResult.fetch(repository, commit.name());

        logger.trace("Commit time is: " + commit.getCommitTime() + "(datetime: " + new DateTime(commit.getCommitTime()) + ")");
        logger.trace("Commit diff is +: " + diff.getAddition() + ", -: " + diff.getDeletion() + ", file: " + diff.getChangedFiles());
        DateTime commitDate = new DateTime(((long) commit.getCommitTime()) * 1000);
        String statement = "INSERT INTO CommitEntry (projectId, developerId, sha1, ordinal, date, additions, deletions, filesChanged) VALUES ('" +
                projectId + "', '" + authorId + "', '" + commit.name() + "', " + count + ", '" + commitDate.toString() +
                "', " + diff.getAddition() + ", " + diff.getDeletion() + ", " + diff.getChangedFiles() + ")";

        return statement;
    }

    private int insertAuthor(String emailAddress) {
        String developerQuery = "SELECT id FROM Developer where username = '" + emailAddress + "'";

        // Try to insert the developer if not exist
        String authorInsert = "INSERT INTO Developer (username) VALUES ('" + emailAddress + "');";
        persistence.addStatements(authorInsert);
        persistence.commit();

        // Try to insert the developer/project mapping if not exist
        String authorProjectInsert = "INSERT INTO ProjectDeveloper (developerId, projectId) VALUES (("
                + developerQuery + "), " + projectId + ");";
        persistence.addStatements(authorProjectInsert);
        persistence.commit();

        // Retrieve developer ID for further usage
        List<Map<String, Object>> result = persistence.query(developerQuery);
        // TODO: Add more verification clauses
        return (int) result.get(0).get("id");
    }

    private void insertFileModification(RevCommit commit) {
        // TODO: See if we can use Jgit instead of a raw call to git
        try {
//            String[] command = {"git", "-C " + cloneDir, "show", commit.name(), "-M" + SIMILARITY_THRESHOLD + "%", "--summary", "--format=''"};
            String command = "git -C "+cloneDir+" show "+commit.name()+" -M50% --summary --format=";
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String commitSelect = "SELECT id from CommitEntry WHERE sha1 ='" + commit.name() + "'";

            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String errLine;
            while ((errLine = stdError.readLine()) != null) {
                logger.error(errLine);
            }

            String line = reader.readLine();
            while (line != null) {
                logger.error("  current line => " + line);
                handleRenameLine(commitSelect, line);
                line = reader.readLine();
            }

        } catch (IOException e) {
            logger.error("Unable to execute git command", e);
        }
    }

    /**
     * Parse and add insertion statement in case of a renamed entry in the git diff.
     *
     * @param commitSelect Query to select the right commit id.
     * @param line         Line to parse for rename.
     */
    private void handleRenameLine(String commitSelect, String line) {
        // TODO: extract
        if (line.trim().startsWith("rename")) {
            GitRenameParser.RenameParsingResult result = null;
            try {
                result = GitRenameParser.parseRenamed(line.trim());
            } catch (Exception e) {
                logger.warn("Unable to parse file rename: ", e.getMessage());
                return;
            }
            if (result.oldFile.endsWith(".java") && result.newFile.endsWith(".java")) {
                String statement = "INSERT INTO FileRename (projectId, commitId, oldFile, newFile, similarity) VALUES ('" +
                        projectId + "',(" + commitSelect + "), '" + result.oldFile + "', '" +
                        result.newFile + "', " + result.similarity + ")";
                persistence.addStatements(statement);
                persistence.commit();
            }
        }
    }

    private Git initializeRepository() throws QueryException {
        Git gitRepo;
        try {
            gitRepo = Git.cloneRepository()
                    .setDirectory(cloneDir.toFile())
                    .setURI("https://github.com/" + this.repository)
                    .call();
        } catch (GitAPIException e) {
            logger.error("Unable to clone repository: " + this.repository, e);
            throw new QueryException(logger.getName(), e);
        }
        return gitRepo;
    }

    private void finalizeRepository() {
        FilesUtils.recursiveDeletion(cloneDir);
    }
}
