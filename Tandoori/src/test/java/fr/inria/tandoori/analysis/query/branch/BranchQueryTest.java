package fr.inria.tandoori.analysis.query.branch;

import fr.inria.tandoori.analysis.model.Commit;
import fr.inria.tandoori.analysis.model.Repository;
import fr.inria.tandoori.analysis.persistence.Persistence;
import fr.inria.tandoori.analysis.query.QueryException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BranchQueryTest {

    private final int projectId = 1;
    private Persistence persistence;
    private Repository repository;

    @Before
    public void setUp() throws Exception {
        repository = Mockito.mock(Repository.class);
        persistence = Mockito.mock(Persistence.class);
        doReturn("BranchInsertion").when(persistence).branchInsertionStatement(eq(projectId), anyInt(), anyBoolean());
        doReturn("BranchInsertion").when(persistence).branchCommitInsertionQuery(eq(projectId), anyInt(), anyString());
    }

    private void initializeHead(Commit commit) throws IOException {
        doReturn(commit).when(repository).getHead();
    }

    /**
     * Register all the given commits to be returned by getCommit.
     *
     * @param commits Input order does not matter as they are referenced by their sha.
     * @throws IOException
     */
    private void initializeMocks(Commit... commits) throws IOException {
        for (Commit commit : commits) {
            doReturn(commit).when(repository).getCommitWithParents(commit.sha);
        }
    }

    /**
     * Testing this kind of branching form (no branch):
     * . A
     * |
     * . B
     * |
     * . C
     *
     * @throws QueryException
     * @throws IOException
     */
    @Test
    public void testNoMerge() throws QueryException, IOException {
        Commit A = new Commit("a", 1);
        Commit B = new Commit("b", 2, Collections.singletonList(A));
        Commit C = new Commit("c", 3, Collections.singletonList(B));

        initializeHead(C);
        initializeMocks(A, B, C);
        BranchQuery query = new BranchQuery(projectId, repository, persistence);

        query.query();

        verify(persistence, times(4)).addStatements(any());
        verify(persistence).branchInsertionStatement(projectId, 0, true);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, A.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, B.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, C.sha);
    }

    /**
     * Testing this kind of branching form (1 branch):
     * .   A
     * |\
     * . | B
     * | . D
     * . | C
     * | . E
     * |/
     * .   F
     *
     * @throws QueryException
     * @throws IOException
     */
    @Test
    public void testSingleMergeCommit() throws QueryException, IOException {
        Commit A = new Commit("a", 1);
        Commit B = new Commit("b", 2, Collections.singletonList(A));
        Commit C = new Commit("c", 3, Collections.singletonList(B));
        Commit D = new Commit("d", 5, Collections.singletonList(A));
        Commit E = new Commit("e", 4, Collections.singletonList(D));
        Commit F = new Commit("f", 6, Arrays.asList(C, E));

        initializeHead(F);
        initializeMocks(A, B, C, D, E, F);
        BranchQuery query = new BranchQuery(projectId, repository, persistence);

        query.query();

        verify(persistence, times(8)).addStatements(any());
        verify(persistence).branchInsertionStatement(projectId, 0, true);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, A.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, B.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, C.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, F.sha);

        verify(persistence).branchInsertionStatement(projectId, 1, false);
        verify(persistence).branchCommitInsertionQuery(projectId, 1, D.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 1, E.sha);
    }

    /**
     * Testing this kind of branching form (2 consecutive branches):
     * .   A
     * |\
     * . | B
     * | . D
     * . | C
     * | . E
     * |/
     * .   F
     * |\
     * | . G
     * . | H
     * |/
     * .   I
     * @throws QueryException
     * @throws IOException
     */
    @Test
    public void testSuccessiveBranches() throws QueryException, IOException {
        Commit A = new Commit("a", 1);
        Commit B = new Commit("b", 2, Collections.singletonList(A));
        Commit C = new Commit("c", 3, Collections.singletonList(B));
        Commit D = new Commit("d", 5, Collections.singletonList(A));
        Commit E = new Commit("e", 4, Collections.singletonList(D));
        Commit F = new Commit("f", 6, Arrays.asList(C, E));
        Commit G = new Commit("g", 7, Collections.singletonList(F));
        Commit H = new Commit("h", 8, Collections.singletonList(F));
        Commit I = new Commit("i", 9, Arrays.asList(H, G));

        initializeHead(I);
        initializeMocks(A, B, C, D, E, F, G, H, I);
        BranchQuery query = new BranchQuery(projectId, repository, persistence);

        query.query();

        verify(persistence, times(12)).addStatements(any());
        verify(persistence).branchInsertionStatement(projectId, 0, true);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, A.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, B.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, C.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, F.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, H.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, I.sha);

        verify(persistence).branchInsertionStatement(projectId, 1, false);
        verify(persistence).branchCommitInsertionQuery(projectId, 1, G.sha);

        verify(persistence).branchInsertionStatement(projectId, 2, false);
        verify(persistence).branchCommitInsertionQuery(projectId, 2, D.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 2, E.sha);
    }

    /**
     * Testing this kind of branching form (2 overlapping branches):
     * .     A
     * |\
     * . |   B
     * | .   D
     * . |\  C
     * | | . E
     * | . | F
     * | |/
     * | .   G
     * . |   H
     * |/
     * .     I
     *
     * @throws QueryException
     * @throws IOException
     */
    @Test
    public void testOverlappingBranches() throws QueryException, IOException {
        Commit A = new Commit("a", 1);
        Commit B = new Commit("b", 2, Collections.singletonList(A));
        Commit C = new Commit("c", 3, Collections.singletonList(B));
        Commit D = new Commit("d", 5, Collections.singletonList(A));
        Commit E = new Commit("e", 4, Collections.singletonList(D));
        Commit F = new Commit("f", 6, Collections.singletonList(D));
        Commit G = new Commit("g", 7, Arrays.asList(F, E));
        Commit H = new Commit("h", 8, Collections.singletonList(C));
        Commit I = new Commit("i", 9, Arrays.asList(H, G));

        initializeHead(I);
        initializeMocks(A, B, C, D, E, F, G, H, I);
        BranchQuery query = new BranchQuery(projectId, repository, persistence);

        query.query();

        verify(persistence, times(12)).addStatements(any());
        verify(persistence).branchInsertionStatement(projectId, 0, true);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, A.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, B.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, C.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, H.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, I.sha);

        verify(persistence).branchInsertionStatement(projectId, 1, false);
        verify(persistence).branchCommitInsertionQuery(projectId, 1, D.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 1, F.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 1, G.sha);

        verify(persistence).branchInsertionStatement(projectId, 2, false);
        verify(persistence).branchCommitInsertionQuery(projectId, 2, E.sha);
    }

    /**
     * Testing this kind of branching form (2 parallel branches):
     *   .   A
     *  /|\
     * | . | B
     * | | . D
     * | . | C
     * . | | E
     * | | . F
     * | | |
     *  \| . G
     *   . | H
     *   |/
     *   .   I
     *
     * @throws QueryException
     * @throws IOException
     */
    @Test
    public void testParallelBranches() throws QueryException, IOException {
        Commit A = new Commit("a", 1);
        Commit B = new Commit("b", 2, Collections.singletonList(A));
        Commit C = new Commit("c", 3, Collections.singletonList(B));
        Commit D = new Commit("d", 5, Collections.singletonList(A));
        Commit E = new Commit("e", 4, Collections.singletonList(A));
        Commit F = new Commit("f", 6, Collections.singletonList(D));
        Commit G = new Commit("g", 7, Collections.singletonList(F));
        Commit H = new Commit("h", 8, Arrays.asList(C, E));
        Commit I = new Commit("i", 9, Arrays.asList(H, G));

        initializeHead(I);
        initializeMocks(A, B, C, D, E, F, G, H, I);
        BranchQuery query = new BranchQuery(projectId, repository, persistence);

        query.query();

        verify(persistence, times(12)).addStatements(any());
        verify(persistence).branchInsertionStatement(projectId, 0, true);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, A.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, B.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, C.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, H.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 0, I.sha);

        verify(persistence).branchInsertionStatement(projectId, 1, false);
        verify(persistence).branchCommitInsertionQuery(projectId, 1, D.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 1, F.sha);
        verify(persistence).branchCommitInsertionQuery(projectId, 1, G.sha);

        verify(persistence).branchInsertionStatement(projectId, 2, false);
        verify(persistence).branchCommitInsertionQuery(projectId, 2, E.sha);
    }
}