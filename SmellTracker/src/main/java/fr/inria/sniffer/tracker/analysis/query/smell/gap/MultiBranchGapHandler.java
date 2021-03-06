package fr.inria.sniffer.tracker.analysis.query.smell.gap;

import fr.inria.sniffer.tracker.analysis.model.Commit;
import fr.inria.sniffer.tracker.analysis.persistence.Persistence;
import fr.inria.sniffer.tracker.analysis.persistence.queries.BranchQueries;

import java.util.List;
import java.util.Map;

public class MultiBranchGapHandler implements CommitGapHandler {
    private final Persistence persistence;
    private final BranchQueries branchQueries;
    private final int projectId;
    private final int branchId;

    public MultiBranchGapHandler(int projectId, int branchId, Persistence persistence, BranchQueries branchQueries) {
        this.persistence = persistence;
        this.branchQueries = branchQueries;
        this.projectId = projectId;
        this.branchId = branchId;
    }

    @Override
    public boolean hasGap(Commit first, Commit second) {
        return Math.abs(second.getBranchOrdinal() - first.getBranchOrdinal()) > 1;
    }

    @Override
    public Commit fetchNoSmellCommit(Commit previous) throws CommitNotFoundException {
        int branchOrdinal = previous.getBranchOrdinal() + 1;
        List<Map<String, Object>> result = persistence.query(branchQueries.shaFromOrdinalQuery(projectId, branchId, branchOrdinal, true));
        if (result.isEmpty() || result.get(0).get("sha1") == null) {
            throw new CommitNotFoundException(projectId, previous.getOrdinal() + 1);
        }
        Commit commit = new Commit(String.valueOf(result.get(0).get("sha1")), previous.getOrdinal());
        commit.setBranchOrdinal(branchOrdinal);
        return commit;
    }

}
