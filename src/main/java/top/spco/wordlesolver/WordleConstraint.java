package top.spco.wordlesolver;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordleConstraint {
    private final int length;
    private File wordList;
    private final HashMap<Integer, HashSet<Character>> cannotBe = new HashMap<>();
    private final HashSet<Character> hasWords = new HashSet<>();
    private final HashMap<Integer, Character> mustBe = new HashMap<>();
    private final HashMap<Character, Integer> minCharCounts = new HashMap<>();
    private final HashMap<Character, Integer> maxCharCounts = new HashMap<>();

    public WordleConstraint(int length) {
        this.length = length;
    }

    public WordleConstraint() {
        this(5);
    }

    public int getLength() {
        return length;
    }

    public Set<String> allWords() {
        if (wordList == null) {
            return new HashSet<>();
        }
        Set<String> words = toWordList(wordList);
        HashSet<String> filtered = new HashSet<>();
        for (String word : words) {
            if (word.length() == length) {
                filtered.add(word);
            }
        }
        return filtered;
    }

    public HashSet<String> answers() {
        HashSet<String> matchingWords = new HashSet<>();
        if (wordList != null) {
            Pattern pattern = Pattern.compile(toRegex(), Pattern.CASE_INSENSITIVE);
            for (String line : toWordList(wordList)) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    matchingWords.add(line);
                }
            }
            return matchingWords;
        }
        return matchingWords;
    }



    public String toRegex() {   
        if (hasWords.isEmpty() && minCharCounts.isEmpty() && maxCharCounts.isEmpty() && mustBe.isEmpty() && cannotBe.isEmpty()) {
            return "^[A-Z]{" + length + "}$";
        }

        StringBuilder regex = new StringBuilder("^");
        for (Map.Entry<Character, Integer> entry : minCharCounts.entrySet()) {
            char c = entry.getKey();
            int count = entry.getValue();
            if (count > 0) {
                regex.append("(?=(?:.*").append(c).append("){").append(count).append(",})");
            }
        }
        for (Map.Entry<Character, Integer> entry : maxCharCounts.entrySet()) {
            char c = entry.getKey();
            int count = entry.getValue();
            if (count >= 0) {
                regex.append("(?!(?:.*").append(c).append("){").append(count + 1).append(",})");
            }
        }
        for (char c : this.hasWords) {
            if (!minCharCounts.containsKey(c)) {
                regex.append("(?=(?:.*").append(c).append("){1,})");
            }
        }
        for (int i = 0; i < length; i++) {
            if (mustBe.containsKey(i)) {
                regex.append(mustBe.get(i));
                continue;
            }
            if (cannotBe.containsKey(i) && !cannotBe.get(i).isEmpty()) {
                regex.append("[A-Z&&[^");
                HashSet<Character> set = cannotBe.get(i);
                for (char c : set) {
                    regex.append(c);
                }
                regex.append("]]");
            } else {
                regex.append("[A-Z]");
            }
        }
        regex.append("$");
        return regex.toString();
    }

    public void setWordList(File wordList) {
        this.wordList = wordList;
    }

    public WordleConstraint setLetter(int letterPos, char c) {
        if (isValidPos(letterPos)) {
            mustBe.put(letterPos - 1, Character.toUpperCase(c));
        }
        return this;
    }

    public WordleConstraint setLetter(int letterPos, String cs) {
        return setLetter(letterPos, stringToChars(cs, true)[0]);
    }

    public WordleConstraint notHave(char... cs) {
        for (int i = 1; i <= this.length; i++) {
            cannotBe(i, cs);
        }
        for (char c : cs) {
            maxCharCounts.put(Character.toUpperCase(c), 0);
        }
        return this;
    }

    public WordleConstraint notHave(String cs) {
        return notHave(stringToChars(cs, false));
    }

    public WordleConstraint cannotBe(int letterPos, char... cs) {
        if (!isValidPos(letterPos)) {
            return this;
        }
        HashSet<Character> set;
        if (cannotBe.containsKey(letterPos - 1)) {
            set = cannotBe.get(letterPos - 1);
        } else {
            set = new HashSet<>();
        }
        for (char c : cs) {
            set.add(Character.toUpperCase(c));
        }
        cannotBe.put(letterPos - 1, set);
        return this;
    }

    public WordleConstraint cannotBe(int letterPos, String chars) {
        return cannotBe(letterPos, stringToChars(chars, false));
    }

    public WordleConstraint hasWord(char... cs) {
        for (char c : cs) {
            char upper = Character.toUpperCase(c);
            hasWords.add(upper);
            updateMinCount(upper, 1);
        }
        return this;
    }

    public WordleConstraint hasWord(String cs) {
        return hasWord(stringToChars(cs, false));
    }

    public WordleConstraint yellowBlock(int letterPos, char c) {
        cannotBe(letterPos, c);
        hasWord(c);
        return this;
    }

    public WordleConstraint yellowBlock(int letterPos, String cs) {
        char[] chars = stringToChars(cs, false);
        for (char c : chars) {
            yellowBlock(letterPos, c);
        }
        return this;
    }

    public WordleConstraint yellowBlock(String word) {
        if (word == null) {
            return this;
        }
        if (word.length() > this.length) {
            throw new IllegalArgumentException("word length is too long");
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '_' || c == 'x' || c == ' ') {
                continue;
            }
            yellowBlock(i + 1, word.charAt(i));
        }
        return this;
    }

    public WordleConstraint mustBe(String word) {
        if (word == null) {
            return this;
        }
        if (word.length() > this.length) {
            throw new IllegalArgumentException("word length is too long");
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '_' || c == 'x' || c == ' ') {
                continue;
            }
            mustBe.put(i, c);
        }
        return this;
    }


    private char[] stringToChars(String str, boolean singleOnly) {
        char[] chars = str.toUpperCase().toCharArray();
        if (singleOnly && chars.length > 1) {
            throw new IllegalArgumentException("Only one single character allowed");
        }
        return chars;
    }

    private boolean isValidPos(int letterPos) {
        return letterPos >= 1 && letterPos <= this.length;
    }

    public static Set<String> toWordList(File file) {
        HashSet<String> wordList = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.toUpperCase();
                wordList.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return wordList;
    }

    public WordleConstraint guess(String guessResult) {
        LetterColor inColor = null;
        List<Character> letters = new ArrayList<>();
        List<LetterColor> colors = new ArrayList<>();
        int pos = 0;
        for (char c : guessResult.toCharArray()) {
            if (Character.isUpperCase(c)) {
                pos++;
                if (pos > this.length) {
                    throw new IllegalArgumentException("Word length is too long.");
                }
                if (inColor == null) {
                    throw new IllegalArgumentException("Color of undefined letters.");
                }
                letters.add(c);
                colors.add(inColor);
            } else {
                inColor = LetterColor.toLetterColor(String.valueOf(c));
                if (inColor == null) {
                    throw new IllegalArgumentException("Unknown color: " + c + ".");
                }
            }
        }
        if (pos != this.length) {
            throw new IllegalArgumentException("The guess does not match the word length.");
        }

        HashMap<Character, Integer> nonBlackCounts = new HashMap<>();
        HashMap<Character, Integer> blackCounts = new HashMap<>();
        for (int i = 0; i < letters.size(); i++) {
            char letter = letters.get(i);
            LetterColor color = colors.get(i);
            if (color == LetterColor.BLACK) {
                blackCounts.put(letter, blackCounts.getOrDefault(letter, 0) + 1);
            } else {
                nonBlackCounts.put(letter, nonBlackCounts.getOrDefault(letter, 0) + 1);
            }
        }

        for (Map.Entry<Character, Integer> entry : nonBlackCounts.entrySet()) {
            updateMinCount(entry.getKey(), entry.getValue());
            hasWords.add(entry.getKey());
        }
        for (Map.Entry<Character, Integer> entry : blackCounts.entrySet()) {
            char letter = entry.getKey();
            int maxCount = nonBlackCounts.getOrDefault(letter, 0);
            updateMaxCount(letter, maxCount);
        }

        for (int i = 0; i < letters.size(); i++) {
            char letter = letters.get(i);
            LetterColor color = colors.get(i);
            int letterPos = i + 1;
            switch (color) {
                case BLACK -> {
                    if (nonBlackCounts.getOrDefault(letter, 0) > 0) {
                        cannotBe(letterPos, letter);
                    } else {
                        notHave(letter);
                    }
                }
                case GREEN -> setLetter(letterPos, letter);
                case YELLOW -> cannotBe(letterPos, letter);
            }
        }
        return this;
    }

    private void updateMinCount(char c, int min) {
        char upper = Character.toUpperCase(c);
        int current = minCharCounts.getOrDefault(upper, 0);
        if (min > current) {
            minCharCounts.put(upper, min);
        }
    }

    private void updateMaxCount(char c, int max) {
        char upper = Character.toUpperCase(c);
        int current = maxCharCounts.getOrDefault(upper, Integer.MAX_VALUE);
        if (max < current) {
            maxCharCounts.put(upper, max);
        }
    }


    public enum LetterColor {
        YELLOW,
        BLACK,
        GREEN;

        public static LetterColor toLetterColor(String code) {
            for (LetterColor letterColor : LetterColor.values()) {
                if (code.equals(String.valueOf(letterColor.name().charAt(0)).toLowerCase())) {
                    return letterColor;
                }
            }
            return null;
        }
    }
}
