package top.spco.wordlesolver;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        WordleConstraint solver = new WordleConstraint();
        WordleScorer scorer = new WordleScorer();
        solver.setWordList(new File("/Users/spco/IdeaProjects/WordleSolver/src/main/resources/answers"));
        solver.guess("bSTyAgRbE")
                .guess("bHgAbIgRY")
        ;
        System.out.println(scorer.rankCandidates(solver).getFirst());
    }
}
