package fr.inria.sniffer.tracker.analysis.query.smell;

import fr.inria.sniffer.tracker.analysis.persistence.queries.SmellQueries;
import fr.inria.sniffer.tracker.analysis.persistence.Persistence;
import fr.inria.sniffer.tracker.analysis.persistence.queries.BranchQueries;
import fr.inria.sniffer.tracker.analysis.persistence.queries.CommitQueries;
import fr.inria.sniffer.tracker.analysis.query.QueryException;
import fr.inria.sniffer.tracker.analysis.query.smell.duplication.SmellDuplicationChecker;
import fr.inria.sniffer.tracker.analysis.query.smell.gap.MultiBranchGapHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Specialized branch fr.inria.sniffer.detector.analyzer which will fetch commit ordinal in the current branch
 * rather than in the 'commit_entry' table.
 */
class MultiBranchAnalyzer extends BranchAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(MultiBranchAnalyzer.class.getName());

    private final BranchQueries branchQueries;
    private final int branchId;

    MultiBranchAnalyzer(int projectId, Persistence persistence, SmellDuplicationChecker duplicationChecker,
                        CommitQueries commitQueries, SmellQueries smellQueries, BranchQueries branchQueries, int branchId, String parentCommitSha) {
        super(projectId, persistence, duplicationChecker, commitQueries, smellQueries, new MultiBranchGapHandler(projectId, branchId, persistence, branchQueries), parentCommitSha);
        this.branchQueries = branchQueries;
        this.branchId = branchId;
    }


    @Override
    public void notifyEnd() throws QueryException {
        super.notifyEnd(fetchLastBranchCommitSha());
    }
    /**
     * Retrieve the sha1 of the last branch's commit analyzed by Paprika.
     *
     * @return The current Paprika HEAD sha1 for the branch.
     * @throws QueryException If we could not find the last paprika Commit, this should not happen.
     */
    private String fetchLastBranchCommitSha() throws QueryException {
        List<Map<String, Object>> result = persistence.query(branchQueries.lastCommitShaQuery(projectId, branchId));
        if (result.isEmpty()) {
            throw new QueryException(logger.getName(), "Unable to fetch last commit for project: " + projectId);
        }
        return (String) result.get(0).get("sha1");
    }
}
