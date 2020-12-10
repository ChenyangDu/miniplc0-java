package miniplc0java;

import miniplc0java.error.CompileError;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class AnalyserTest {
    public static void main(String[] args) throws CompileError {
//        double a = 1.2;
//        System.out.println(Double.doubleToLongBits(a));
        String[] strings = {"-l", "data/in.txt","-o", "data/output.bin"};
//        String[] strings = {"-t", "data/in.txt","-o", "data/output.txt"};
        App.main(strings);
    }
}
