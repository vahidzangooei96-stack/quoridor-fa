package ir.quoridorfa.core;

import java.util.List;
import java.util.Random;

/**
 * Pure-JVM test suite for the Quoridor core.
 * Run: java ir.quoridorfa.core.QuoridorCoreTest
 * Exit code 0 = all pass, 1 = failure.
 */
public class QuoridorCoreTest {

    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        testInitialSetup();
        testInitialMoves();
        testWallBlocksMove();
        testStraightJump();
        testDiagonalSidestep();
        testDiagonalSidestepBlockedBySideWall();
        testEdgeSidestep();
        testWallOverlapAndCrossing();
        testActionGuards();
        testPathBlockingRejected();
        testWallCountAndTurnFlip();
        testWinDetection();
        test500RandomGames();
        testAiFindsImmediateWin();
        testAiPreventsOpponentWin();
        testAiVsAiFullGame();
        testAiTimeBudget();

        System.out.println();
        System.out.println("========================================");
        System.out.println("PASSED: " + passed + "   FAILED: " + failed);
        if (failed > 0) System.exit(1);
        System.out.println("ALL TESTS PASSED");
    }

    // ---------- helpers ----------

    private static void check(boolean cond, String name) {
        if (cond) { passed++; System.out.println("[PASS] " + name); }
        else      { failed++; System.out.println("[FAIL] " + name); }
    }

    private static boolean hasMove(GameCore g, int p, int r, int c) {
        List<int[]> mv = g.legalPawnMoves(p);
        for (int[] m : mv) if (m[0] == r && m[1] == c) return true;
        return false;
    }

    // ---------- rule tests ----------

    static void testInitialSetup() {
        GameCore g = new GameCore();
        check(g.pawnR[0] == 8 && g.pawnC[0] == 4, "setup: P0 starts at (8,4)");
        check(g.pawnR[1] == 0 && g.pawnC[1] == 4, "setup: P1 starts at (0,4)");
        check(g.wallsLeft[0] == 10 && g.wallsLeft[1] == 10, "setup: 10 walls each");
        check(g.turn == 0 && g.winner == -1, "setup: P0 to move, no winner");
    }

    static void testInitialMoves() {
        GameCore g = new GameCore();
        List<int[]> mv = g.legalPawnMoves(0);
        check(mv.size() == 3
                && hasMove(g, 0, 7, 4) && hasMove(g, 0, 8, 3) && hasMove(g, 0, 8, 5),
                "rules: P0 initial legal moves = up/left/right (3 moves)");
    }

    static void testWallBlocksMove() {
        GameCore g = new GameCore();
        check(g.placeWall(true, 7, 4), "wall: place H wall at (7,4) accepted");
        check(!hasMove(g, 0, 7, 4), "wall: H(7,4) blocks (8,4)->(7,4)");
        check(hasMove(g, 0, 8, 3) && hasMove(g, 0, 8, 5), "wall: side moves unaffected");
        check(!hasMove(g, 0, 7, 5), "wall: H(7,4) also blocks (8,5)->(7,5)");
    }

    static void testStraightJump() {
        GameCore g = new GameCore();
        g.pawnR[0] = 4; g.pawnC[0] = 4;
        g.pawnR[1] = 3; g.pawnC[1] = 4;
        check(hasMove(g, 0, 2, 4), "jump: straight jump over opponent to (2,4)");
        check(!hasMove(g, 0, 3, 3) && !hasMove(g, 0, 3, 5),
                "jump: no diagonal sidestep while straight jump is free");
    }

    static void testDiagonalSidestep() {
        GameCore g = new GameCore();
        g.pawnR[0] = 4; g.pawnC[0] = 4;
        g.pawnR[1] = 3; g.pawnC[1] = 4;
        g.hWall[2][3] = true; g.hWall[2][4] = true; // wall behind opponent blocks landing
        check(hasMove(g, 0, 3, 3) && hasMove(g, 0, 3, 5),
                "jump: blocked jump -> diagonal side-step (3,3)/(3,5)");
        check(!hasMove(g, 0, 2, 4), "jump: straight jump not offered when blocked");
    }

    static void testDiagonalSidestepBlockedBySideWall() {
        GameCore g = new GameCore();
        g.pawnR[0] = 4; g.pawnC[0] = 4;
        g.pawnR[1] = 3; g.pawnC[1] = 4;
        g.hWall[2][3] = true; g.hWall[2][4] = true; // straight jump blocked
        g.vWall[3][3] = true; // blocks opponent's edge to the left side-step
        check(!hasMove(g, 0, 3, 3), "jump: side-step rejected when a perpendicular wall blocks it");
        check(hasMove(g, 0, 3, 5), "jump: unblocked opposite side-step remains legal");
    }

    static void testEdgeSidestep() {
        GameCore g = new GameCore();
        g.pawnR[0] = 1; g.pawnC[0] = 4;
        g.pawnR[1] = 0; g.pawnC[1] = 4; // opponent on board edge
        check(hasMove(g, 0, 0, 3) && hasMove(g, 0, 0, 5),
                "jump: opponent at edge -> diagonal side-step");
        check(!hasMove(g, 0, 0, 4), "jump: no off-board straight jump");
    }

    static void testWallOverlapAndCrossing() {
        GameCore g = new GameCore();
        check(g.canPlaceWall(true, 4, 4), "wall: H(4,4) placeable");
        check(g.placeWall(true, 4, 4), "wall: H(4,4) placed");
        check(!g.canPlaceWall(true, 4, 4), "wall: same H anchor rejected");
        check(!g.canPlaceWall(false, 4, 4), "wall: crossing V at same anchor rejected");
        check(!g.canPlaceWall(true, 4, 5), "wall: overlapping adjacent H anchor rejected");
        check(g.canPlaceWall(true, 4, 6), "wall: non-overlapping H anchor with one-cell gap allowed");
        check(g.canPlaceWall(false, 3, 4), "wall: perpendicular V at non-crossing anchor allowed");
        GameCore v = new GameCore();
        check(v.placeWall(false, 3, 4), "wall: place V(3,4)");
        check(!v.canPlaceWall(false, 4, 4), "wall: overlapping adjacent V anchor rejected");
        check(v.canPlaceWall(false, 5, 4), "wall: non-overlapping V anchor allowed");
    }

    static void testActionGuards() {
        GameCore g = new GameCore();
        check(!g.applyAction(null), "action: null action rejected");
        check(!g.applyAction(new int[]{99, 0, 0}), "action: unknown type rejected");
        check(!g.applyAction(new int[]{GameCore.ACT_WALL_H, 0, 99}), "action: out-of-range wall rejected");
        check(g.allActions(1).isEmpty(), "action: actions for non-current player rejected");
    }

    static void testPathBlockingRejected() {
        GameCore g = new GameCore();
        g.pawnR[0] = 0; g.pawnC[0] = 0; // P0 in the corner
        g.pawnR[1] = 4; g.pawnC[1] = 4;
        check(g.canPlaceWall(true, 0, 0), "path: first wall at corner OK (escape via col 1)");
        g.placeWall(true, 0, 0);
        check(g.shortestPath(0) < GameCore.INF, "path: P0 still has a route");
        check(!g.canPlaceWall(false, 0, 0), "path: sealing wall rejected (P0 would be locked)");
    }

    static void testWallCountAndTurnFlip() {
        GameCore g = new GameCore();
        g.placeWall(true, 3, 3);
        check(g.wallsLeft[0] == 9 && g.wallsLeft[1] == 10, "wall count: P0 wall consumed");
        check(g.turn == 1, "turn: flips to P1 after wall");
        check(g.moveCount == 1, "ply counter: incremented");
    }

    static void testWinDetection() {
        GameCore g = new GameCore();
        g.pawnR[0] = 1; g.pawnC[0] = 4;
        g.pawnR[1] = 0; g.pawnC[1] = 8; // move P1 away from (0,4)
        g.applyPawnMove(0, 4);
        check(g.winner == 0, "win: P0 reaching row 0 wins");
        g.reset();
        g.pawnR[1] = 7; g.pawnC[1] = 4; g.turn = 1;
        g.pawnR[0] = 8; g.pawnC[0] = 0; // move P0 away from (8,4)
        g.applyPawnMove(8, 4);
        check(g.winner == 1, "win: P1 reaching row 8 wins");
    }

    // ---------- random-game stress (design doc quality gate) ----------

    static void test500RandomGames() {
        Random rnd = new Random(20260101);
        int finished = 0, totalWalls = 0, totalPlies = 0, maxPlies = 0;
        boolean invariantHeld = true;
        for (int i = 0; i < 500; i++) {
            GameCore g = new GameCore();
            int plies = 0;
            while (g.winner == -1 && plies < 1500) {
                List<int[]> actions = g.allActions(g.turn);
                if (actions.isEmpty()) break;
                int[] a = actions.get(rnd.nextInt(actions.size()));
                boolean isWall = a[0] != GameCore.ACT_MOVE;
                if (isWall) totalWalls++;
                boolean ok = g.applyAction(a);
                if (!ok) { invariantHeld = false; break; }
                // Core invariant: a WALL never seals any player. (After a PAWN move a
                // player may legally walk into a dead-end pocket — that is allowed.)
                if (isWall && (g.shortestPath(0) == GameCore.INF || g.shortestPath(1) == GameCore.INF)) {
                    invariantHeld = false; break;
                }
                plies++;
            }
            totalPlies += plies;
            maxPlies = Math.max(maxPlies, plies);
            if (g.winner != -1) finished++;
        }
        check(invariantHeld, "stress: wall path invariant held after every wall in 500 games");
        check(finished >= 400, "stress: random games terminate (" + finished + "/500 finished, avg "
                + (totalPlies / 500) + " plies, max " + maxPlies + ", walls " + totalWalls + ")");
    }

    // ---------- AI tests ----------

    static void testAiFindsImmediateWin() {
        GameCore g = new GameCore();
        g.pawnR[0] = 1; g.pawnC[0] = 4; // one step from goal
        g.pawnR[1] = 0; g.pawnC[1] = 8;
        g.hWall[0][0] = true; // some noise
        QuoridorAi ai = new QuoridorAi(42);
        int[] a = ai.chooseAction(g, 0, QuoridorAi.HARD);
        check(a != null && a[0] == GameCore.ACT_MOVE && a[1] == 0 && a[2] == 4,
                "ai: P0 takes immediate winning move to (0,4)");
    }

    static void testAiPreventsOpponentWin() {
        GameCore g = new GameCore();
        g.pawnR[0] = 1; g.pawnC[0] = 0;  // P0 threatens (0,0)
        g.pawnR[1] = 5; g.pawnC[1] = 4;  // AI far from goal
        g.turn = 1;
        QuoridorAi ai = new QuoridorAi(7);
        int[] a = ai.chooseAction(g, 1, QuoridorAi.MEDIUM);
        check(a != null, "ai: action returned");
        boolean applied = g.applyAction(a);
        check(applied, "ai: chosen action is legal");
        // After AI's reply, P0 must have no one-step win left
        boolean oppCanWin = false;
        for (int[] m : g.legalPawnMoves(0)) if (m[0] == 0) oppCanWin = true;
        check(!oppCanWin, "ai: AI prevents P0's one-step win (H wall at (0,0))");
    }

    static void testAiVsAiFullGame() {
        GameCore g = new GameCore();
        QuoridorAi ai = new QuoridorAi(99);
        long t0 = System.currentTimeMillis();
        int plies = 0;
        while (g.winner == -1 && plies < 300) {
            int[] a = ai.chooseAction(g, g.turn, QuoridorAi.MEDIUM);
            if (a == null || !g.applyAction(a)) break;
            plies++;
        }
        long dt = System.currentTimeMillis() - t0;
        check(g.winner != -1, "ai: AI-vs-AI game terminates (" + plies + " plies, winner=P"
                + g.winner + ", " + dt + " ms total)");
    }

    static void testAiTimeBudget() {
        GameCore g = new GameCore();
        // dense mid-game position to stress the search
        g.hWall[3][3] = true; g.hWall[4][4] = true; g.vWall[3][5] = true;
        g.vWall[5][2] = true; g.hWall[5][6] = true; g.vWall[2][7] = true;
        QuoridorAi ai = new QuoridorAi(1234);
        long t0 = System.currentTimeMillis();
        int[] a = ai.chooseAction(g, 0, QuoridorAi.HARD);
        long dt = System.currentTimeMillis() - t0;
        check(a != null, "ai: HARD returns an action in a dense position");
        check(dt <= 2500, "ai: HARD respects the 1.5s time budget (took " + dt + " ms)");
    }
}
