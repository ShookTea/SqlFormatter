package eu.shooktea.sqlformatter;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

public class SqlFormatter {

    private static final Set<String> BEGIN_CLAUSES = new HashSet<>();
    private static final Set<String> END_CLAUSES = new HashSet<>();
    private static final Set<String> LOGICAL = new HashSet<>();
    private static final Set<String> QUANTIFIERS = new HashSet<>();
    private static final Set<String> DML = new HashSet<>();
    private static final Set<String> MISC = new HashSet<>();
    private static final String INITIAL;

    static {
        BEGIN_CLAUSES.add("left");
        BEGIN_CLAUSES.add("right");
        BEGIN_CLAUSES.add("inner");
        BEGIN_CLAUSES.add("outer");
        BEGIN_CLAUSES.add("group");
        BEGIN_CLAUSES.add("order");
        END_CLAUSES.add("where");
        END_CLAUSES.add("set");
        END_CLAUSES.add("having");
        END_CLAUSES.add("join");
        END_CLAUSES.add("from");
        END_CLAUSES.add("by");
        END_CLAUSES.add("into");
        END_CLAUSES.add("union");
        LOGICAL.add("and");
        LOGICAL.add("or");
        LOGICAL.add("when");
        LOGICAL.add("else");
        LOGICAL.add("end");
        QUANTIFIERS.add("in");
        QUANTIFIERS.add("all");
        QUANTIFIERS.add("exists");
        QUANTIFIERS.add("some");
        QUANTIFIERS.add("any");
        DML.add("insert");
        DML.add("update");
        DML.add("delete");
        MISC.add("select");
        MISC.add("on");
        INITIAL = System.lineSeparator() + "    ";
    }

    private static class FormatProcess {
        boolean beginLine = true;
        boolean afterBeginBeforeEnd;
        boolean afterByOrSetOrFromOrSelect;
        boolean afterValues;
        boolean afterOn;
        boolean afterBetween;
        boolean afterInsert;
        int inFunction;
        int parensSinceSelect;
        private LinkedList<Integer> parenCounts = new LinkedList<>();
        private LinkedList<Boolean> afterByOrFromOrSelects = new LinkedList<>();
        int indent = 1;
        StringBuilder result = new StringBuilder();
        StringTokenizer tokens;
        String lastToken;
        String token;
        String lcToken;

        FormatProcess(String sql) {
            this.tokens = new StringTokenizer(sql, "()+*/-=<>'`\"[], \n\r\f\t", true);
        }

        String perform() {
            this.result.append(SqlFormatter.INITIAL);

            while(this.tokens.hasMoreTokens()) {
                this.token = this.tokens.nextToken();
                this.lcToken = this.token.toLowerCase(Locale.ROOT);
                String t;
                if ("'".equals(this.token)) {
                    do {
                        t = this.tokens.nextToken();
                        this.token = this.token + t;
                    } while(!"'".equals(t) && this.tokens.hasMoreTokens());
                } else if ("\"".equals(this.token)) {
                    do {
                        t = this.tokens.nextToken();
                        this.token = this.token + t;
                    } while(!"\"".equals(t) && this.tokens.hasMoreTokens());
                } else if ("[".equals(this.token)) {
                    do {
                        t = this.tokens.nextToken();
                        this.token = this.token + t;
                    } while(!"]".equals(t) && this.tokens.hasMoreTokens());
                }

                if (this.afterByOrSetOrFromOrSelect && ",".equals(this.token)) {
                    this.commaAfterByOrFromOrSelect();
                } else if (this.afterOn && ",".equals(this.token)) {
                    this.commaAfterOn();
                } else if ("(".equals(this.token)) {
                    this.openParen();
                } else if (")".equals(this.token)) {
                    this.closeParen();
                } else if (SqlFormatter.BEGIN_CLAUSES.contains(this.lcToken)) {
                    this.beginNewClause();
                } else if (SqlFormatter.END_CLAUSES.contains(this.lcToken)) {
                    this.endNewClause();
                } else if ("select".equals(this.lcToken)) {
                    this.select();
                } else if (SqlFormatter.DML.contains(this.lcToken)) {
                    this.updateOrInsertOrDelete();
                } else if ("values".equals(this.lcToken)) {
                    this.values();
                } else if ("on".equals(this.lcToken)) {
                    this.on();
                } else if (this.afterBetween && this.lcToken.equals("and")) {
                    this.misc();
                    this.afterBetween = false;
                } else if (SqlFormatter.LOGICAL.contains(this.lcToken)) {
                    this.logical();
                } else if (isWhitespace(this.token)) {
                    this.white();
                } else {
                    this.misc();
                }

                if (!isWhitespace(this.token)) {
                    this.lastToken = this.lcToken;
                }
            }

            return this.result.toString();
        }

        private void commaAfterOn() {
            this.out();
            --this.indent;
            this.newline();
            this.afterOn = false;
            this.afterByOrSetOrFromOrSelect = true;
        }

        private void commaAfterByOrFromOrSelect() {
            this.out();
            this.newline();
        }

        private void logical() {
            if ("end".equals(this.lcToken)) {
                --this.indent;
            }

            this.newline();
            this.out();
            this.beginLine = false;
        }

        private void on() {
            ++this.indent;
            this.afterOn = true;
            this.newline();
            this.out();
            this.beginLine = false;
        }

        private void misc() {
            this.out();
            if ("between".equals(this.lcToken)) {
                this.afterBetween = true;
            }

            if (this.afterInsert) {
                this.newline();
                this.afterInsert = false;
            } else {
                this.beginLine = false;
                if ("case".equals(this.lcToken)) {
                    ++this.indent;
                }
            }

        }

        private void white() {
            if (!this.beginLine) {
                this.result.append(" ");
            }

        }

        private void updateOrInsertOrDelete() {
            this.out();
            ++this.indent;
            this.beginLine = false;
            if ("update".equals(this.lcToken)) {
                this.newline();
            }

            if ("insert".equals(this.lcToken)) {
                this.afterInsert = true;
            }

        }

        private void select() {
            this.out();
            ++this.indent;
            this.newline();
            this.parenCounts.addLast(this.parensSinceSelect);
            this.afterByOrFromOrSelects.addLast(this.afterByOrSetOrFromOrSelect);
            this.parensSinceSelect = 0;
            this.afterByOrSetOrFromOrSelect = true;
        }

        private void out() {
            this.result.append(this.token);
        }

        private void endNewClause() {
            if (!this.afterBeginBeforeEnd) {
                --this.indent;
                if (this.afterOn) {
                    --this.indent;
                    this.afterOn = false;
                }

                this.newline();
            }

            this.out();
            if (!"union".equals(this.lcToken)) {
                ++this.indent;
            }

            this.newline();
            this.afterBeginBeforeEnd = false;
            this.afterByOrSetOrFromOrSelect = "by".equals(this.lcToken) || "set".equals(this.lcToken) || "from".equals(this.lcToken);
        }

        private void beginNewClause() {
            if (!this.afterBeginBeforeEnd) {
                if (this.afterOn) {
                    --this.indent;
                    this.afterOn = false;
                }

                --this.indent;
                this.newline();
            }

            this.out();
            this.beginLine = false;
            this.afterBeginBeforeEnd = true;
        }

        private void values() {
            --this.indent;
            this.newline();
            this.out();
            ++this.indent;
            this.newline();
            this.afterValues = true;
        }

        private void closeParen() {
            --this.parensSinceSelect;
            if (this.parensSinceSelect < 0) {
                --this.indent;
                this.parensSinceSelect = this.parenCounts.removeLast();
                this.afterByOrSetOrFromOrSelect = this.afterByOrFromOrSelects.removeLast();
            }

            if (this.inFunction > 0) {
                --this.inFunction;
                this.out();
            } else {
                if (!this.afterByOrSetOrFromOrSelect) {
                    --this.indent;
                    this.newline();
                }

                this.out();
            }

            this.beginLine = false;
        }

        private void openParen() {
            if (isFunctionName(this.lastToken) || this.inFunction > 0) {
                ++this.inFunction;
            }

            this.beginLine = false;
            if (this.inFunction > 0) {
                this.out();
            } else {
                this.out();
                if (!this.afterByOrSetOrFromOrSelect) {
                    ++this.indent;
                    this.newline();
                    this.beginLine = true;
                }
            }

            ++this.parensSinceSelect;
        }

        private static boolean isFunctionName(String tok) {
            if (tok != null && tok.length() != 0) {
                char begin = tok.charAt(0);
                boolean isIdentifier = Character.isJavaIdentifierStart(begin) || '"' == begin;
                return isIdentifier && !SqlFormatter.LOGICAL.contains(tok) && !SqlFormatter.END_CLAUSES.contains(tok) && !SqlFormatter.QUANTIFIERS.contains(tok) && !SqlFormatter.DML.contains(tok) && !SqlFormatter.MISC.contains(tok);
            } else {
                return false;
            }
        }

        private static boolean isWhitespace(String token) {
            return " \n\r\f\t".contains(token);
        }

        private void newline() {
            this.result.append(System.lineSeparator());

            for(int i = 0; i < this.indent; ++i) {
                this.result.append("    ");
            }

            this.beginLine = true;
        }
    }

    public static String format(String sql) {
        return (new FormatProcess(sql)).perform();
    }

}
