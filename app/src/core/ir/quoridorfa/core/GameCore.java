package ir.quoridorfa.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Quoridor game core — pure Java, no Android dependencies.
 * Implements the official Gigamic rules:
 *  - 9x9 board, 2 players
 *  - Player 0 starts at (8,4), goal = row 0 (moves up)
 *  - Player 1 starts at (0,4), goal = row 8 (moves down)
 *  - 10 walls each; walls block movement; a wall may never seal off a player's path
 *  - Straight jump over the opponent when the landing square is free,
 *    otherwise a diagonal side-step (official FAQ interpretation)
 *
 * Coordinates: row 0 = top, col 0 = left.
 * Walls: hWall[r][c] blocks passage between rows r and r+1 for columns c and c+1.
 *        vWall[r][c] blocks passage between columns c and c+1 for rows r and r+1.
 *        r,c in 0..7. An H wall and a V wall may not cross at the same anchor.
 */
public class GameCore {

    public static final int N = 9;               // board size (cells)
    public static final int WN = 8;              // wall anchors per axis
    public static final int WALLS_PER_PLAYER = 10;
    public static final int PAWN_0 = 0, PAWN_1 = 1;

    // Directions
    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    public static final int[] DR = {-1, 1, 0, 0};
    public static final int[] DC = {0, 0, -1, 1};

    /** A requested action: type 0 = pawn move to (a,b); type 1 = wall (a = H(0)/V(1), b = r*WN+c). */
    public static final int ACT_MOVE = 0, ACT_WALL_H = 1, ACT_WALL_V = 2;

    public final boolean[][] hWall = new boolean[WN][WN];
    public final boolean[][] vWall = new boolean[WN][WN];
    public final int[] pawnR = new int[2];
    public final int[] pawnC = new int[2];
    public final int[] wallsLeft = new int[2];
    public int turn;            // whose turn: 0 or 1
    public int winner = -1;     // -1 = playing, otherwise index of winner
    public int moveCount = 0;   // plies played
    public final int[] lastMove = new int[4]; // r0,c0 -> r1,c1 of last pawn move (for UI)

    public GameCore() {
        reset();
    }

    public final void reset() {
        for (int r = 0; r < WN; r++)
            for (int c = 0; c < WN; c++) { hWall[r][c] = false; vWall[r][c] = false; }
        pawnR[0] = 8; pawnC[0] = 4;
        pawnR[1] = 0; pawnC[1] = 4;
        wallsLeft[0] = WALLS_PER_PLAYER;
        wallsLeft[1] = WALLS_PER_PLAYER;
        turn = 0;
        winner = -1;
        moveCount = 0;
        lastMove[0] = lastMove[1] = lastMove[2] = lastMove[3] = -1;
    }

    /** Lightweight deep copy (used by the AI search). */
    public GameCore copy() {
        GameCore g = new GameCore();
        g.copyFrom(this);
        return g;
    }

    public void copyFrom(GameCore o) {
        for (int r = 0; r < WN; r++)
            for (int c = 0; c < WN; c++) { hWall[r][c] = o.hWall[r][c]; vWall[r][c] = o.vWall[r][c]; }
        pawnR[0] = o.pawnR[0]; pawnC[0] = o.pawnC[0];
        pawnR[1] = o.pawnR[1]; pawnC[1] = o.pawnC[1];
        wallsLeft[0] = o.wallsLeft[0]; wallsLeft[1] = o.wallsLeft[1];
        turn = o.turn; winner = o.winner; moveCount = o.moveCount;
        System.arraycopy(o.lastMove, 0, lastMove, 0, 4);
    }

    public static boolean onBoard(int r, int c) {
        return r >= 0 && r < N && c >= 0 && c < N;
    }

    public int opponentOf(int p) { return p ^ 1; }

    /** Is the orthogonal move from (r,c) in direction d blocked by a wall? */
    public boolean wallBlocked(int r, int c, int d) {
        // Cell coords r,c in 0..8; wall anchors in 0..7 (WN) — always bounds-check
        // the anchor index that can equal 8 (row index for V walls, col index for H walls).
        switch (d) {
            case UP:   // passage between rows r-1 and r
                if (r <= 0) return false;
                if (c < WN && hWall[r - 1][c]) return true;   // anchor covers cols c, c+1
                return c > 0 && hWall[r - 1][c - 1];          // anchor covers cols c-1, c
            case DOWN: // passage between rows r and r+1
                if (r >= N - 1) return false;
                if (c < WN && hWall[r][c]) return true;
                return c > 0 && hWall[r][c - 1];
            case LEFT: // passage between columns c-1 and c
                if (c <= 0) return false;
                if (r < WN && vWall[r][c - 1]) return true;   // anchor covers rows r, r+1
                return r > 0 && vWall[r - 1][c - 1];          // anchor covers rows r-1, r
            case RIGHT:// passage between columns c and c+1
                if (c >= N - 1) return false;
                if (r < WN && vWall[r][c]) return true;
                return r > 0 && vWall[r - 1][c];
        }
        return false;
    }

    /** All legal pawn moves for player p (official jump & side-step rules applied). */
    public List<int[]> legalPawnMoves(int p) {
        List<int[]> res = new ArrayList<>(4);
        int r = pawnR[p], c = pawnC[p];
        int op = p ^ 1;
        for (int d = 0; d < 4; d++) {
            int nr = r + DR[d], nc = c + DC[d];
            if (!onBoard(nr, nc) || wallBlocked(r, c, d)) continue;
            if (nr == pawnR[op] && nc == pawnC[op]) {
                // Opponent occupies the target: straight jump if landing is free
                int fr = r + 2 * DR[d], fc = c + 2 * DC[d];
                if (onBoard(fr, fc) && !wallBlocked(nr, nc, d)) {
                    res.add(new int[]{fr, fc});
                } else {
                    // Straight jump blocked (wall or edge) -> diagonal side-step,
                    // both perpendicular squares (official FAQ: no extra wall check
                    // is applied to the diagonal itself)
                    for (int pd = 0; pd < 4; pd++) {
                        if (pd == d || pd == (d ^ 1)) continue; // perpendicular only
                        int tr = nr + DR[pd], tc = nc + DC[pd];
                        if (onBoard(tr, tc)) res.add(new int[]{tr, tc});
                    }
                }
            } else {
                res.add(new int[]{nr, nc});
            }
        }
        return res;
    }

    /** Geometry + path check for placing a wall of orientation h at anchor (r,c). */
    public boolean canPlaceWall(boolean horizontal, int r, int c) {
        if (winner != -1) return false;
        if (wallsLeft[turn] <= 0) return false;
        if (r < 0 || r >= WN || c < 0 || c >= WN) return false;
        if (horizontal) {
            if (hWall[r][c]) return false;
            if (vWall[r][c]) return false; // crossing at the same anchor
        } else {
            if (vWall[r][c]) return false;
            if (hWall[r][c]) return false;
        }
        if (horizontal) hWall[r][c] = true; else vWall[r][c] = true;
        boolean ok = shortestPath(0) < INF && shortestPath(1) < INF;
        if (horizontal) hWall[r][c] = false; else vWall[r][c] = false;
        return ok;
    }

    public boolean placeWall(boolean horizontal, int r, int c) {
        if (!canPlaceWall(horizontal, r, c)) return false;
        if (horizontal) hWall[r][c] = true; else vWall[r][c] = true;
        wallsLeft[turn]--;
        moveCount++;
        turn = turn ^ 1;
        return true;
    }

    /** Apply a pawn move for the player on turn. Returns false if illegal. */
    public boolean applyPawnMove(int toR, int toC) {
        if (winner != -1) return false;
        List<int[]> moves = legalPawnMoves(turn);
        for (int[] m : moves) {
            if (m[0] == toR && m[1] == toC) {
                lastMove[0] = pawnR[turn]; lastMove[1] = pawnC[turn];
                pawnR[turn] = toR; pawnC[turn] = toC;
                lastMove[2] = toR; lastMove[3] = toC;
                moveCount++;
                if ((turn == 0 && toR == 0) || (turn == 1 && toR == N - 1)) {
                    winner = turn;
                }
                turn = turn ^ 1;
                return true;
            }
        }
        return false;
    }

    public static final int INF = 9999;

    /**
     * BFS shortest path length from player p's pawn to p's goal row.
     * Walls count, the opposing pawn does NOT block (standard interpretation).
     * Returns INF if unreachable.
     */
    public int shortestPath(int p) {
        int goalRow = (p == 0) ? 0 : N - 1;
        if (pawnR[p] == goalRow) return 0;
        boolean[][] seen = new boolean[N][N];
        int[] qr = new int[N * N], qc = new int[N * N], qd = new int[N * N];
        int head = 0, tail = 0;
        qr[tail] = pawnR[p]; qc[tail] = pawnC[p]; qd[tail] = 0; tail++;
        seen[pawnR[p]][pawnC[p]] = true;
        int op = p ^ 1;
        while (head < tail) {
            int r = qr[head], c = qc[head], d = qd[head]; head++;
            for (int dir = 0; dir < 4; dir++) {
                int nr = r + DR[dir], nc = c + DC[dir];
                if (!onBoard(nr, nc) || seen[nr][nc] || wallBlocked(r, c, dir)) continue;
                if (nr == pawnR[op] && nc == pawnC[op]) {
                    // BFS for path legality ignores the pawn as an obstacle,
                    // but a jump is not required here: allow passing THROUGH the
                    // opponent square only if a real step exists beyond; simplest
                    // correct model used by all rule engines: treat opponent square
                    // as walkable (pawns move away during play).
                    // -> fall through (do not skip)
                }
                seen[nr][nc] = true;
                if (nr == goalRow) return d + 1;
                qr[tail] = nr; qc[tail] = nc; qd[tail] = d + 1; tail++;
            }
        }
        return INF;
    }

    /** All legal actions for the player on turn, encoded as {type, a, b}: 0=move(a=r,b=c), 1=H wall(b=r*WN+c), 2=V wall(b=r*WN+c). */
    public List<int[]> allActions(int p) {
        List<int[]> res = new ArrayList<>();
        for (int[] m : legalPawnMoves(p)) res.add(new int[]{ACT_MOVE, m[0], m[1]});
        if (wallsLeft[p] > 0 && winner == -1) {
            for (int r = 0; r < WN; r++)
                for (int c = 0; c < WN; c++) {
                    if (hWall[r][c] || vWall[r][c]) continue;
                    if (canPlaceWall(true, r, c)) res.add(new int[]{ACT_WALL_H, 0, r * WN + c});
                    if (canPlaceWall(false, r, c)) res.add(new int[]{ACT_WALL_V, 0, r * WN + c});
                }
        }
        return res;
    }

    /** Apply an encoded action (see allActions). Returns false if illegal. */
    public boolean applyAction(int[] a) {
        if (a[0] == ACT_MOVE) return applyPawnMove(a[1], a[2]);
        int r = a[2] / WN, c = a[2] % WN;
        return placeWall(a[0] == ACT_WALL_H, r, c);
    }

    /** Undo-support primitives used by the AI (apply + revert). */
    public int[] snapshot() {
        return new int[]{pawnR[0], pawnC[0], pawnR[1], pawnC[1],
                wallsLeft[0], wallsLeft[1], turn, winner, moveCount,
                lastMove[0], lastMove[1], lastMove[2], lastMove[3]};
    }

    public void restore(int[] s) {
        pawnR[0] = s[0]; pawnC[0] = s[1]; pawnR[1] = s[2]; pawnC[1] = s[3];
        wallsLeft[0] = s[4]; wallsLeft[1] = s[5];
        turn = s[6]; winner = s[7]; moveCount = s[8];
        System.arraycopy(s, 9, lastMove, 0, 4);
    }
}
