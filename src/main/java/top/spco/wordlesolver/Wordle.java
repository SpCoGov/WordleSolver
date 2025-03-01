package top.spco.wordlesolver;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import top.spco.wordlesolver.WordleSolver.LetterColor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Wordle {
    private final String answer;
    private final int maxAttempts;
    private int attempts = 0;
    private boolean win = false;
    private boolean lose = false;

    public Wordle(String answer) {
        this(answer, 6);
    }

    public Wordle(String answer, int maxGuessTimes) {
        this.answer = answer.toLowerCase();
        this.maxAttempts = maxGuessTimes;
    }

    public String guess(String guess) {
        if (isGameOver()) {
            return "GAME OVER";
        }
        attempts++;
        StringBuilder output = new StringBuilder();
        LetterColor previousColor = null;
        List<LetterColor> testResult = testWord(guess);
        if (testResult.stream().allMatch(color -> color == LetterColor.GREEN)) {
            win = true;
            return "GAME OVER";
        }
        if (attempts >= maxAttempts) {
            lose = true;
            return "GAME OVER";
        }
        for (int i = 0; i < testResult.size(); i++) {
            LetterColor current = testResult.get(i);
            if (current != previousColor) {
                output.append(Character.toLowerCase(current.name().charAt(0)));
            }
            output.append(Character.toUpperCase(guess.charAt(i)));
            previousColor = current;
        }
        return output.toString();
    }

    public boolean isGameOver() {
        return win || lose;
    }

    public boolean isWin() {
        return win;
    }

    public boolean isLose() {
        return lose;
    }

    /**
     * 测试玩家的猜测并返回颜色结果
     *
     * @param guess 玩家猜测的单词
     * @return 颜色结果列表
     */
    public List<LetterColor> testWord(String guess) {
        guess = guess.toLowerCase(); // 确保猜测是小写
        List<LetterColor> result = new ArrayList<>();
        Map<Character, Integer> counter = new HashMap<>();

        // 统计答案中每个字母的出现次数
        for (char c : answer.toCharArray()) {
            counter.put(c, counter.getOrDefault(c, 0) + 1);
        }

        // 第一轮：检查绿色匹配
        for (int i = 0; i < guess.length(); i++) {
            if (guess.charAt(i) == answer.charAt(i)) {
                result.add(LetterColor.GREEN);
                // 减少该字母的计数
                counter.put(guess.charAt(i), counter.get(guess.charAt(i)) - 1);
            } else {
                result.add(null); // 暂时占位，后续再更新
            }
        }

        // 第二轮：检查黄色匹配
        for (int i = 0; i < guess.length(); i++) {
            if (result.get(i) == LetterColor.GREEN) {
                continue; // 已匹配为绿色的跳过
            }

            char c = guess.charAt(i);
            if (counter.getOrDefault(c, 0) > 0) {
                result.set(i, LetterColor.YELLOW); // 标记为黄色
                counter.put(c, counter.get(c) - 1); // 减少计数
            } else {
                result.set(i, LetterColor.BLACK); // 标记为灰色
            }
        }

        return result;
    }

    public String getAnswer() {
        return answer;
    }

    public int getAttempts() {
        return attempts;
    }

    public static Wordle start(int maxAttempts, int length, File answers) {
        try {
            List<String> lines = Files.readAllLines(answers.toPath());
            List<String> validLines = lines.stream()
                    .filter(line -> line.length() == length)
                    .toList();
            if (validLines.isEmpty()) {
                throw new IllegalArgumentException("No lines with length " + length + " found.");
            }
            Random random = new Random();
            int randomIndex = random.nextInt(validLines.size());
            return new Wordle(validLines.get(randomIndex), maxAttempts);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start wordle game.", e);
        }
    }

    public static void main(String[] args) {
        int testTimes = 10000;
        int testedTimes = 0;
        ArrayList<Integer> attempts = new ArrayList<>();
        int winTimes = 0;
        ProgressBarBuilder progressBarBuilder = ProgressBar.builder();
        progressBarBuilder.setInitialMax(testTimes);
        progressBarBuilder.setUpdateIntervalMillis(100);
        progressBarBuilder.setStyle(ProgressBarStyle.ASCII);
        ProgressBar progressBar = progressBarBuilder.build();

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < testTimes; i++) {
            Wordle wordle = start(6, 5, new File("E:\\MyCodes\\WordleSolver\\src\\main\\resources\\answers"));
            WordleSolver solver = new WordleSolver();
            solver.setWordList(new File("E:\\MyCodes\\WordleSolver\\src\\main\\resources\\answers"));
            LinkedHashMap<String,String> attemptResultMap = new LinkedHashMap<>();
            try {
                do {
                    String attemptWord = solver.printAnswers(true, true).getFirst();
                    String result = wordle.guess(attemptWord);
                    attemptResultMap.put(attemptWord, result);
                    if (result.equals("GAME OVER")) {
                        break;
                    }
                    solver.guess(result);
                } while (!wordle.isGameOver());
                progressBar.step();
                attempts.add(wordle.getAttempts());
                testedTimes++;
                if (wordle.isWin()) {
                    winTimes++;
                } else {
                    StringBuilder sb = new StringBuilder("\nNo.").append(i).append(" lose: \n");
                    for (var entry : attemptResultMap.entrySet()) {
                        sb.append(entry.getKey().toLowerCase()).append(": ").append(entry.getValue()).append("\n");
                    }
                    sb.append("Answer: ").append(wordle.getAnswer());
                    System.out.println(sb);
                }
            } catch (Exception e) {
                StringBuilder sb = new StringBuilder("\nNo.").append(i).append(" error: \n");
                for (var entry : attemptResultMap.entrySet()) {
                    sb.append(entry.getKey().toLowerCase()).append(": ").append(entry.getValue()).append("\n");
                }
                sb.append("Answer: ").append(wordle.getAnswer());
                System.out.println(sb);
            }
        }
        long endTime = System.currentTimeMillis();
        System.out.println();
        System.out.println("Total Time: " + (endTime - startTime) / 1000.0 + " seconds");
        System.out.println("Planned number of tests: " + testTimes);
        System.out.println("Completed tests: " + testedTimes);
        System.out.println("Wins: " + winTimes);
        System.out.println("Win rate: " + (winTimes * 100.0 / testTimes));
        double averageAttempts = calculateAverage(attempts);
        System.out.println("Average attempts: " + averageAttempts);

    }

    private static double calculateAverage(ArrayList<Integer> attempts) {
        if (attempts.isEmpty()) {
            throw new IllegalArgumentException("The attempts list is empty.");
        }

        // 计算总和
        int sum = 0;
        for (int attempt : attempts) {
            sum += attempt;
        }

        // 计算平均值
        return (double) sum / attempts.size();
    }
}
