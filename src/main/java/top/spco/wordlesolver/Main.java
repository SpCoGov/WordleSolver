package top.spco.wordlesolver;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        WordleConstraint solver = new WordleConstraint();
        solver.setWordList(new File("/Users/spco/IdeaProjects/WordleSolver/src/main/resources/answers"));
        solver.guess("gSTbARE")
                .guess("gSTObNY")
                .guess("gSTObMP")
        ;
        System.out.println(solver.printAnswers(true).getFirst());
    }
}
