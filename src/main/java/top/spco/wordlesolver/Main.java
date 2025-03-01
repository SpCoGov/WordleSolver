package top.spco.wordlesolver;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        WordleSolver solver = new WordleSolver();
        solver.setWordList(new File("E:\\MyCodes\\WordleSolver\\src\\main\\resources\\answers"));
                ;
        System.out.println(solver.printAnswers(true).getFirst());
    }
}
