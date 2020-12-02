package miniplc0java;

import miniplc0java.error.CompileError;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class AnalyserTest {
    public static void main(String[] args) throws CompileError {
        String[] strings = {"-l", "in.txt","-o", "output.bin"};
        App.main(strings);
    }
}
