package fr.inria.sniffer.tracker.analysis.persistence.queries;

import fr.inria.sniffer.tracker.analysis.model.Commit;
import fr.inria.sniffer.tracker.analysis.model.GitChangedFile;
import fr.inria.sniffer.tracker.analysis.model.GitDiff;
import fr.inria.sniffer.tracker.analysis.model.GitRename;

public class JDBCCommitQueries extends JDBCQueriesHelper implements CommitQueries {

    private DeveloperQueries developerQueries;

    public JDBCCommitQueries(DeveloperQueries developerQueries) {
        this.developerQueries = developerQueries;
    }

    @Override
    public String commitInsertionStatement(int projectId, Commit commit, GitDiff diff) {
        logger.trace("[" + projectId + "] Inserting commit: " + commit.sha
                + " - ordinal: " + commit.ordinal + " - diff: " + diff + " - time: " + commit.date);

        // Escaping double dollars to avoid exiting dollar quoted string too soon.
        String commitMessage = escapeStringEntry(commit.message);

        String mergedCommit = commit.getParentCount() >= 2 ?
                "(" + idFromShaQuery(projectId, commit.getParent(1).sha) + ")" : null;

        String developerQuery = developerQueries.idFromEmailQuery(commit.authorEmail);
        return "INSERT INTO commit_entry (project_id, developer_id, sha1, ordinal, date, " +
                "additions, deletions, files_changed, message, merged_commit_id, in_detector) VALUES ('" +
                projectId + "', (" + developerQuery + "), '" + commit.sha + "', " + commit.ordinal + ", '" + commit.date.toString() +
                "', " + diff.getAddition() + ", " + diff.getDeletion() + ", " + diff.getChangedFiles() +
                ", $$ " + commitMessage + " $$, " + mergedCommit + ", " + commit.isInPaprika() + ") ON CONFLICT DO NOTHING;";
    }

    @Override
    public String idFromShaQuery(int projectId, String sha) {
        return idFromShaQuery(projectId, sha, false);
    }

    @Override
    public String shaFromOrdinalQuery(int projectId, int ordinal) {
        return shaFromOrdinalQuery(projectId, ordinal, false);
    }

    @Override
    public String idFromShaQuery(int projectId, String sha, boolean paprikaOnly) {
        String query = "SELECT id FROM commit_entry WHERE sha1 = '" + sha + "' AND project_id = " + projectId;
        if (paprikaOnly) {
            query += " AND in_detector IS TRUE";
        }
        return query;
    }

    @Override
    public String shaFromOrdinalQuery(int projectId, int ordinal, boolean paprikaOnly) {
        String query = "SELECT sha1 FROM commit_entry WHERE ordinal = '" + ordinal + "' AND project_id = " + projectId;
        if (paprikaOnly) {
            query += " AND in_detector IS TRUE";
        }
        return query;
    }

    @Override
    public String lastProjectCommitShaQuery(int projectId) {
        return lastProjectCommitShaQuery(projectId, false);
    }

    @Override
    public String lastProjectCommitShaQuery(int projectId, boolean paprikaOnly) {
        String query = "SELECT sha1 FROM commit_entry WHERE project_id = '" + projectId + "'";
        if (paprikaOnly) {
            query += " AND in_detector IS TRUE";
        }
        query += " ORDER BY ordinal DESC LIMIT 1";

        return query;
    }

    @Override
    public String fileRenameInsertionStatement(int projectId, String commitSha, GitRename rename) {
        return "INSERT INTO file_rename (project_id, commit_id, old_file, new_file, similarity) VALUES ('" +
                projectId + "', (" + idFromShaQuery(projectId, commitSha) + "), '" + rename.oldFile + "', '" +
                rename.newFile + "', " + rename.similarity + ") ON CONFLICT DO NOTHING;";
    }

    @Override
    public String mergedCommitIdQuery(int projectId, Commit commit) {
        return "SELECT merged_commit_id AS id FROM commit_entry where sha1 = '" + commit.sha + "'";
    }

    @Override
    public String projectIdFromShaQuery(String sha) {
        return "SELECT project_id from commit_entry WHERE sha1 = '" + sha + "'";
    }

    @Override
    public String updateCommitSizeQuery(int projectId, String tempTable) {
        return "UPDATE commit_entry " +
                "SET number_of_classes = " + tempTable + ".number_of_classes, " +
                "number_of_methods = " + tempTable + ".number_of_methods, " +
                "number_of_views = " + tempTable + ".number_of_views, " +
                "number_of_activities = " + tempTable + ".number_of_activities, " +
                "number_of_inner_classes = " + tempTable + ".number_of_inner_classes " +
                "FROM " + tempTable + " " +
                "WHERE  commit_entry.sha1 = " + tempTable + ".sha1 " +
                "AND commit_entry.project_id = " + projectId;
    }

    @Override
    public String fileChangedInsertionStatement(int projectId, String commitSha, GitChangedFile changedFile) {
        return "INSERT INTO file_changed (project_id, commit_id, file_name, modification_size) VALUES ('" +
                projectId + "', (" + idFromShaQuery(projectId, commitSha) + "), '" + escapeStringEntry(changedFile.name) + "', '" +
                changedFile.changeSize + "') ON CONFLICT DO NOTHING;";
    }

}
