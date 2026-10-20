package ir.quoridorfa.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

import ir.quoridorfa.core.GameCore;

/**
 * Custom board view: 9x9 grid, pawns, walls, legal-move dots, drag-to-place
 * ghost walls. Pure framework code, API 21+.
 */
public class BoardView extends View {

    public interface GameCallback {
        void onPawnMoveRequested(int r, int c);
        void onWallRequested(boolean horizontal, int r, int c);
    }

    private GameCore game;
    private GameCallback callback;
    private List<int[]> legalTargets;
    private boolean wallMode = false;
    private boolean defaultWallH = true;
    private boolean locked = false;

    // ghost wall (drag preview)
    private boolean ghostActive = false;
    private int ghostR, ghostC;
    private boolean ghostH = true;
    private boolean ghostLegal = false;
    private float downX, downY;

    // geometry
    private float cell, boardLeft, boardTop, wallT;

    private final Paint pFrame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGoal0 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGoal1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLast = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pWall = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGhostOk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGhostBad = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPawn0 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPawn1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPawnRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pInter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    public BoardView(Context context) {
        super(context);
        pFrame.setColor(0xFF4E342E);
        pCell.setColor(0xFFEAD9B8);
        pGoal0.setColor(0x3314B8A6);
        pGoal1.setColor(0x33EF5350);
        pLast.setColor(0x33FFFFFF);
        pDot.setColor(0xB30288D1);
        pWall.setColor(0xFF3E2723);
        pGhostOk.setColor(0xC82E7D32);
        pGhostBad.setColor(0xC8B71C1C);
        pPawn0.setColor(0xFF0F9D8F);
        pPawn1.setColor(0xFFD32F2F);
        pPawnRing.setColor(0xFFFFFFFF);
        pPawnRing.setStyle(Paint.Style.STROKE);
        pGlow.setColor(0xFFFFD54F);
        pGlow.setStyle(Paint.Style.STROKE);
        pInter.setColor(0x403E2723);
    }

    public void setCallback(GameCallback cb) { callback = cb; }
    public void setWallMode(boolean wm) { wallMode = wm; ghostActive = false; }
    public void setDefaultWallH(boolean h) { defaultWallH = h; }
    public void setLocked(boolean l) { locked = l; ghostActive = false; }

    /** Call after every state change; recomputes highlights and redraws. */
    public void refresh() {
        legalTargets = null;
        if (game != null && game.winner == -1 && !locked && game.turn == 0 && !wallMode) {
            legalTargets = game.legalPawnMoves(0);
        }
        invalidate();
    }

    public void setGame(GameCore g) {
        game = g;
        refresh();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeGeometry(w, h);
    }

    private void computeGeometry(int w, int h) {
        float side = Math.min(w, h);
        cell = side / 9.75f;                    // 9 cells + wall overhang margins
        boardLeft = (w - 9 * cell) / 2f;
        boardTop = (h - 9 * cell) / 2f;
        wallT = cell * 0.22f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (game == null) return;
        if (cell == 0) computeGeometry(getWidth(), getHeight());
        float boardSize = 9 * cell;
        float over = cell * 0.30f;

        // frame
        rf.set(boardLeft - over, boardTop - over, boardLeft + boardSize + over, boardTop + boardSize + over);
        canvas.drawRoundRect(rf, cell * 0.24f, cell * 0.24f, pFrame);

        // cells
        float gap = Math.max(1.5f, cell * 0.035f);
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                rf.set(boardLeft + c * cell + gap, boardTop + r * cell + gap,
                        boardLeft + (c + 1) * cell - gap, boardTop + (r + 1) * cell - gap);
                canvas.drawRoundRect(rf, cell * 0.10f, cell * 0.10f, pCell);
            }
        }
        // goal rows
        for (int c = 0; c < 9; c++) {
            rf.set(boardLeft + c * cell + gap, boardTop + gap,
                    boardLeft + (c + 1) * cell - gap, boardTop + cell - gap);
            canvas.drawRect(rf, pGoal0);
            rf.set(boardLeft + c * cell + gap, boardTop + 8 * cell + gap,
                    boardLeft + (c + 1) * cell - gap, boardTop + 9 * cell - gap);
            canvas.drawRect(rf, pGoal1);
        }

        // last pawn move highlight
        if (game.lastMove[0] >= 0) {
            int tr = game.lastMove[2], tc = game.lastMove[3];
            rf.set(boardLeft + tc * cell + gap, boardTop + tr * cell + gap,
                    boardLeft + (tc + 1) * cell - gap, boardTop + (tr + 1) * cell - gap);
            canvas.drawRect(rf, pLast);
        }

        // legal move dots
        if (legalTargets != null && game.turn == 0 && game.winner == -1 && !locked) {
            for (int[] m : legalTargets) {
                canvas.drawCircle(boardLeft + (m[1] + 0.5f) * cell, boardTop + (m[0] + 0.5f) * cell,
                        cell * 0.14f, pDot);
            }
        }

        // wall-mode intersection hints
        if (wallMode && game.winner == -1 && !locked) {
            for (int iy = 1; iy <= 8; iy++)
                for (int ix = 1; ix <= 8; ix++)
                    canvas.drawCircle(boardLeft + ix * cell, boardTop + iy * cell,
                            cell * 0.06f, pInter);
        }

        // walls
        float len = 2 * cell - cell * 0.10f;
        for (int r = 0; r < GameCore.WN; r++) {
            for (int c = 0; c < GameCore.WN; c++) {
                float cx = boardLeft + (c + 1) * cell;
                float cy = boardTop + (r + 1) * cell;
                if (game.hWall[r][c]) {
                    rf.set(cx - len / 2f, cy - wallT / 2f, cx + len / 2f, cy + wallT / 2f);
                    canvas.drawRoundRect(rf, wallT / 2f, wallT / 2f, pWall);
                }
                if (game.vWall[r][c]) {
                    rf.set(cx - wallT / 2f, cy - len / 2f, cx + wallT / 2f, cy + len / 2f);
                    canvas.drawRoundRect(rf, wallT / 2f, wallT / 2f, pWall);
                }
            }
        }

        // ghost wall preview
        if (ghostActive) {
            float cx = boardLeft + (ghostC + 1) * cell;
            float cy = boardTop + (ghostR + 1) * cell;
            Paint gp = ghostLegal ? pGhostOk : pGhostBad;
            if (ghostH) {
                rf.set(cx - len / 2f, cy - wallT / 2f, cx + len / 2f, cy + wallT / 2f);
            } else {
                rf.set(cx - wallT / 2f, cy - len / 2f, cx + wallT / 2f, cy + len / 2f);
            }
            canvas.drawRoundRect(rf, wallT / 2f, wallT / 2f, gp);
        }

        // pawns
        drawPawn(canvas, 0, game.pawnR[0], game.pawnC[0], pPawn0);
        drawPawn(canvas, 1, game.pawnR[1], game.pawnC[1], pPawn1);
    }

    private void drawPawn(Canvas canvas, int player, int r, int c, Paint fill) {
        float cx = boardLeft + (c + 0.5f) * cell;
        float cy = boardTop + (r + 0.5f) * cell;
        float rad = cell * 0.33f;
        if (game.winner == -1 && game.turn == player && !locked) {
            pGlow.setStrokeWidth(cell * 0.06f);
            canvas.drawCircle(cx, cy, rad * 1.28f, pGlow);
        }
        canvas.drawCircle(cx, cy, rad, fill);
        pPawnRing.setStrokeWidth(cell * 0.05f);
        canvas.drawCircle(cx, cy, rad, pPawnRing);
    }

    // ---------- touch ----------

    private boolean inBoard(float x, float y) {
        return x >= boardLeft && x <= boardLeft + 9 * cell
                && y >= boardTop && y <= boardTop + 9 * cell;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (game == null || locked || game.winner != -1) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX(); downY = ev.getY();
                if (wallMode && inBoard(downX, downY)) {
                    updateGhost(downX, downY);
                    ghostH = defaultWallH;
                    ghostActive = true;
                    invalidate();
                    return true;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (wallMode && ghostActive) {
                    float dx = ev.getX() - downX, dy = ev.getY() - downY;
                    if (Math.abs(dx) > Math.abs(dy) * 1.2f) ghostH = true;
                    else if (Math.abs(dy) > Math.abs(dx) * 1.2f) ghostH = false;
                    updateGhost(ev.getX(), ev.getY());
                    invalidate();
                    return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (wallMode && ghostActive) {
                    ghostActive = false;
                    invalidate();
                    if (callback != null) callback.onWallRequested(ghostH, ghostR, ghostC);
                    return true;
                }
                if (!wallMode && inBoard(ev.getX(), ev.getY()) && legalTargets != null && game.turn == 0) {
                    int c = (int) ((ev.getX() - boardLeft) / cell);
                    int r = (int) ((ev.getY() - boardTop) / cell);
                    r = Math.max(0, Math.min(8, r));
                    c = Math.max(0, Math.min(8, c));
                    for (int[] m : legalTargets) {
                        if (m[0] == r && m[1] == c && callback != null) {
                            callback.onPawnMoveRequested(r, c);
                            break;
                        }
                    }
                }
                return true;
        }
        return true;
    }

    private void updateGhost(float x, float y) {
        int ix = Math.round((x - boardLeft) / cell);
        int iy = Math.round((y - boardTop) / cell);
        ix = Math.max(1, Math.min(8, ix));
        iy = Math.max(1, Math.min(8, iy));
        ghostR = iy - 1;
        ghostC = ix - 1;
        ghostLegal = game != null && game.canPlaceWall(ghostH, ghostR, ghostC);
    }
}
