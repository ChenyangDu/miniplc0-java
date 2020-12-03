package miniplc0java.analyser;

import miniplc0java.error.AnalyzeError;
import miniplc0java.error.CompileError;
import miniplc0java.error.ErrorCode;
import miniplc0java.error.ExpectedTokenError;
import miniplc0java.error.TokenizeError;
import miniplc0java.instruction.Instruction;
import miniplc0java.instruction.Operation;
import miniplc0java.program.Functiondef;
import miniplc0java.program.Globaldef;
import miniplc0java.program.Program;
import miniplc0java.tokenizer.Token;
import miniplc0java.tokenizer.TokenType;
import miniplc0java.tokenizer.Tokenizer;
import miniplc0java.util.Pos;

import java.util.*;

public final class Analyser {

    Tokenizer tokenizer;
    ArrayList<Instruction> instructions;

    /** 当前偷看的 token */
    Token peekedToken = null;

    /** 符号表 */
    Symboler symboler = new Symboler();

    /** 程序 */
    public Program program = new Program();

    /** 算符优先文法的栈 */
    List<TokenType> opStack = new ArrayList<>();

    /** 下一个变量的栈偏移 */
    int nextOffset = 0;

    public Analyser(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.instructions = new ArrayList<>();
    }

    public List<Instruction> analyse() throws CompileError {
        analyseProgram();
        System.out.println("\n"+symboler);
        for(SymbolEntry symbol:symboler.symbolTable){
            if(symbol.isGlobal && symbol.type != SymbolType.FUN_NAME){
                program.addGlobal(new Globaldef(symbol.isConstant,"00000000"));
            }
        }

        System.out.println(program);
//        System.out.println(program.toByteString());
//        System.out.println(Arrays.toString(program.toBytes()));
        return instructions;
    }

    /**
     * 查看下一个 Token
     *
     * @return
     * @throws TokenizeError
     */
    private Token peek() throws TokenizeError {
        if (peekedToken == null) {
            peekedToken = tokenizer.nextToken();
        }
        return peekedToken;
    }

    /**
     * 获取下一个 Token
     *
     * @return
     * @throws TokenizeError
     */
    private Token next() throws TokenizeError {
        if (peekedToken != null) {
            var token = peekedToken;
            peekedToken = null;
            return token;
        } else {
            return tokenizer.nextToken();
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则返回 true
     *
     * @param tt
     * @return
     * @throws TokenizeError
     */
    private boolean check(TokenType tt) throws TokenizeError {
        var token = peek();
        return token.getTokenType() == tt;
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回这个 token
     *
     * @param tt 类型
     * @return 如果匹配则返回这个 token，否则返回 null
     * @throws TokenizeError
     */
    private Token nextIf(TokenType tt) throws TokenizeError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            return null;
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回，否则抛出异常
     *
     * @param tt 类型
     * @return 这个 token
     * @throws CompileError 如果类型不匹配
     */
    private Token expect(TokenType tt) throws CompileError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            throw new ExpectedTokenError(tt, token);
        }
    }

    private void analyseProgram() throws CompileError {
        // 先给_start占个坑
        symboler.addSymbol("_start",SymbolType.FUN_NAME,
                false,false,0,true,false,null);
        while (!check(TokenType.EOF)){
            if(check(TokenType.LET_KW)){
                analyseDeclLetStmt(0);
            }else if(check(TokenType.CONST_KW)){
                analyseDeclConstStmt(0);
            }else if(check(TokenType.FN_KW)){
                anslyseFn();
            }else{
                break;
            }
        }
        addStartFun();
        addFunName();
        expect(TokenType.EOF);
    }

    private void addFunName(){
        for(Functiondef fun:program.functiondefList){
            fun.name_id = program.globaldefList.size();
            program.addGlobal(new Globaldef(true,fun.name));
        }
    }

    private void addMain()throws AnalyzeError{
        Functiondef mainFun = program.find("main");
        if(mainFun == null){
            throw new AnalyzeError(ErrorCode.NoMain,null);
        }
        newIns(Operation.CALL,mainFun.id);
    }
    private void addStartFun() throws AnalyzeError {

        addMain();
        Functiondef functiondef = new Functiondef();
        functiondef.name = "_start";
        functiondef.id = 0;
        functiondef.returnSize = 0;
        functiondef.params = new ArrayList<>();
        functiondef.localSize = 0;
        int length = 0;
        for(int i=0;i<instructions.size();i++){
            functiondef.instructions.add(instructions.get(i));
            length += instructions.get(i).toByteString().length()/2;
        }
        functiondef.bodySize = instructions.size(); //(length+7)/8;
        while(instructions.size() > 0){
            instructions.remove(0);
        }
        program.functiondefList.add(0,functiondef);
        SymbolEntry symbolEntry = symboler.findSymbol(functiondef.name);
    }

    private void analyseDeclLetStmt(int level) throws CompileError{
        expect(TokenType.LET_KW);
        Token ident = expect(TokenType.IDENT);
        String name = (String)ident.getValue();
        expect(TokenType.COLON);
        Token type = expect(TokenType.IDENT);
        // 符号表相关
        symboler.addSymbol(name,tokenToSymbolType(type),false,false,level,
                level==0,false,ident.getStartPos());
        if(nextIf(TokenType.ASSIGN) != null){
            pushVar(ident,false);
            analyseExpr();
            newIns(Operation.STORE64);
        }
        expect(TokenType.SEMICOLON);
    }

    private void analyseDeclConstStmt(int level) throws CompileError{
        expect(TokenType.CONST_KW);
        Token ident = expect(TokenType.IDENT);
        String name = (String)ident.getValue();
        expect(TokenType.COLON);
        Token type = expect(TokenType.IDENT);
        symboler.addSymbol(name,tokenToSymbolType(type),false,false,level,
                level==0,false,ident.getStartPos());
        expect(TokenType.ASSIGN);
        pushVar(ident,false);
        analyseExpr();
        newIns(Operation.STORE64);
        expect(TokenType.SEMICOLON);
    }

    private void anslyseFn() throws CompileError{
        Pos startPos;
        // fn关键字
        startPos = expect(TokenType.FN_KW).getStartPos();

        // 获取函数名字
        Functiondef funEntry = new Functiondef();
        funEntry.name = (String)expect(TokenType.IDENT).getValue();
        expect(TokenType.L_PAREN);

        int beforeParamSize = symboler.symbolTable.size();

        // 获取函数参数列表
        while (!check(TokenType.R_PAREN)){
            boolean isConst = false;
            if(nextIf(TokenType.CONST_KW) != null){
                isConst = true;
            }
            Token param = expect(TokenType.IDENT);
            String paramName = (String)param.getValue();
            expect(TokenType.COLON);
            Token paramType = expect(TokenType.IDENT);
            SymbolEntry symbol = new SymbolEntry(paramName,tokenToSymbolType(paramType),
                    isConst,true,1,false,true,funEntry.params.size());
            funEntry.params.add(symbol);
            symboler.addSymbol(symbol);
            if(peek().getTokenType() != TokenType.COMMA){
                break;
            }
            expect(TokenType.COMMA);
        }
        expect(TokenType.R_PAREN);
        expect(TokenType.ARROW);
        Token returnType = expect(TokenType.IDENT);
        if(tokenToSymbolType(returnType) != SymbolType.VOID_NAME){
            funEntry.returnSize = 1;
        }else{
            funEntry.returnSize = 0;
        }
        // 如果函数有返回值，需要把所有参数+1

        if(funEntry.returnSize == 1){
            for(int i=beforeParamSize;i<symboler.symbolTable.size();i++){
                symboler.symbolTable.get(i).stackOffset++;
            }
        }

        // 获取函数的总长度
        int oriSize = instructions.size();
        int oriSymSize = symboler.symbolTable.size();
        // 分析函数主体
        analyseBlockStmt(0);

        // 最后加上return
        newIns(Operation.RET);

        // 获取函数中新增的指令数量
        int length = 0;
        for(int i=oriSize;i<instructions.size();i++){
            funEntry.instructions.add(instructions.get(i));
            length += instructions.get(i).toByteString().length()/2;
        }
        funEntry.bodySize = instructions.size() - oriSize; //(length+7)/8;
        while(instructions.size() > oriSize){
            instructions.remove(oriSize);
        }

        // 获取函数中需要的局部变量数量
        funEntry.localSize = symboler.symbolTable.size() - oriSymSize;

        //todo 建立函数的符号表

        symboler.popAllLevel();
        SymbolEntry symbol = symboler.addSymbol(funEntry.name,SymbolType.FUN_NAME,
                false,false,0,true,false,startPos);
        // 给_start函数留地方
        funEntry.id = symbol.stackOffset;
        program.addFunc(funEntry);
    }

    /*
    stmt ->
      expr_stmt
    | decl_stmt let const
    | if_stmt if
    | while_stmt while
    | break_stmt break
    | continue_stmt continue
    | return_stmt return
    | block_stmt {
    | empty_stmt ;

     */
    private void analyseBlockStmt(int level) throws CompileError{
        level ++;
        expect(TokenType.L_BRACE);
        while(true){
            Token peek = peek();
            TokenType type = peek.getTokenType();
            if (type == TokenType.CONST_KW) {
                analyseDeclConstStmt(level);
            } else if (type == TokenType.LET_KW) {
                analyseDeclLetStmt(level);
            } else if (type == TokenType.IF_KW) {
                analyseIfStmt(level);
            } else if(type == TokenType.WHILE_KW){
                analyseWhile(level);
            } else if(type == TokenType.RETURN_KW){
                analyseReturn();
            } else if(type == TokenType.L_BRACE){
                analyseBlockStmt(level);
            } else if(type == TokenType.SEMICOLON){
                // todo 这咋整？
                next();
            } else if(type == TokenType.R_BRACE) {
                break;
            }else{
                analyseExpr();
            }
        }
        expect(TokenType.R_BRACE);
    }

    private void analyseIfStmt(int level) throws CompileError{
        expect(TokenType.IF_KW);
        analyseExpr();
        int br_false_pos,br_pos;
        br_false_pos = instructions.size();
        newIns(Operation.BR_FALSE);
        analyseBlockStmt(level);
        br_pos = instructions.size();
        System.out.println("after if"+peek());
        if(nextIf(TokenType.ELSE_KW) != null){
            System.out.println("yes get else");
            newIns(Operation.BR);
            instructions.get(br_false_pos).setX(br_pos - br_false_pos);
            if(check(TokenType.IF_KW)){ // else if
                System.out.println("ana else if");
                analyseIfStmt(level);
            }else{
                System.out.println("ana else" + peek());
                analyseBlockStmt(level); // else
            }
            instructions.get(br_pos).setX(instructions.size() - br_pos-1);
        }else{
            instructions.get(br_false_pos).setX(br_pos - br_false_pos-1);
        }
    }

    private void analyseWhile(int level) throws CompileError{
        expect(TokenType.WHILE_KW);
        int start = instructions.size();
        analyseExpr();
        int br_pos = instructions.size();
        newIns(Operation.BR_FALSE);
        analyseBlockStmt(level);
        newIns(Operation.BR,(start - instructions.size() - 1));
        instructions.get(br_pos).setX(instructions.size() - br_pos-1);
    }

    private void analyseReturn() throws CompileError{
        expect(TokenType.RETURN_KW);
        if(nextIf(TokenType.SEMICOLON) == null){
            System.out.println("return "+peek());
            newIns(Operation.ARGA,0);
            analyseExpr();
            newIns(Operation.STORE64);
        }
        newIns(Operation.RET);
    }

    private void analyseExpr() throws CompileError{
        if(!isExprBegin(peek().getTokenType())){
            throw new AnalyzeError(ErrorCode.InvalidInput,peekedToken.getStartPos());
        }
        analyseExprCmp();
//        while (nextIf(TokenType.ASSIGN) != null){
//            analyseExprCmp();
//            // todo
//        }
        check(TokenType.SEMICOLON);
    }

    private boolean isExprBegin(TokenType type){
        return type == TokenType.IDENT ||
                type == TokenType.L_PAREN ||
                type == TokenType.CHAR_LITERAL ||
                type == TokenType.DOUBLE_LITERAL ||
                type == TokenType.UINT_LITERAL ||
                type == TokenType.STRING_LITERAL;
    }


    private void analyseExprCmp() throws CompileError{
        analyseExprPM();
        while (isCmpOp(peek().getTokenType())){
            TokenType type = peek().getTokenType();
            next();
            analyseExprPM();
            switch (type) {
                case GT -> {
                    newIns(Operation.CMP_I);
                    newIns(Operation.SET_GT);
                }
                case LT -> {
                    newIns(Operation.CMP_I);
                    newIns(Operation.SET_LT);
                }
                case GE -> {
                    newIns(Operation.CMP_I);
                    newIns(Operation.SET_LT);
                    newIns(Operation.NOT);
                }
                case LE -> {
                    newIns(Operation.CMP_I);
                    newIns(Operation.SET_GT);
                    newIns(Operation.NOT);
                }
                case EQ -> {
                    newIns(Operation.CMP_I);
                    newIns(Operation.NOT);
                }
                case NEQ -> {
                    newIns(Operation.CMP_I);
                    newIns(Operation.NOT);
                    newIns(Operation.NOT);
                }
            }
        }
    }

    private void analyseExprPM() throws CompileError{
        analyseExprMD();
        TokenType type = peek().getTokenType();
        while (type == TokenType.PLUS || type == TokenType.MINUS){
            Token op = next();
            analyseExprMD();
            //todo double
            if(op.getTokenType() == TokenType.PLUS) {
                newIns(Operation.ADD_I);
            }else{
                newIns(Operation.SUB_I);
            }
            type = peek().getTokenType();
        }
    }

    private void analyseExprMD() throws CompileError{
        analyseExprAS();
        TokenType type = peek().getTokenType();
        while (type == TokenType.MUL || type == TokenType.DIV){
            next();
            analyseExprAS();
            // todo double
            if(type == TokenType.MUL){
                newIns(Operation.MUL_I);
            }else{
                newIns(Operation.DIV_I);
            }
            type = peek().getTokenType();
        }
    }

    private void analyseExprAS() throws CompileError{
        analyseExprSign();
        while (peek().getTokenType() == TokenType.AS_KW){
            next();
            // todo double
            analyseExprSign();
        }
    }

    private void analyseExprSign() throws CompileError{
        TokenType type = peek().getTokenType();
        int minusCnt = 0;
        while(type == TokenType.MINUS){
            //todo double
            next();
            minusCnt++;
            newIns(Operation.PUSH,0);
            type = peek().getTokenType();
        }
        analyseExprItem();
        while ((minusCnt--) !=0){
            newIns(Operation.SUB_I);
        }

    }

    private void analyseExprItem() throws CompileError{
        if(nextIf(TokenType.L_PAREN) != null){
            analyseExpr();
            expect(TokenType.R_PAREN);
        }
        Token token = peek();
        if(token.getTokenType() == TokenType.UINT_LITERAL){
            pushUint(token);
        }else if(token.getTokenType() == TokenType.DOUBLE_LITERAL){
            pushDouble(token);
        }else if(token.getTokenType() == TokenType.IDENT){
            pushIdent(token);
        }else if(token.getTokenType() == TokenType.STRING_LITERAL){
            pushString(token);
        }
    }

    private void pushString(Token token) throws CompileError {
        expect(TokenType.STRING_LITERAL);
        String value = (String)token.getValue();
        for(int i=value.length()-1;i>=0;i--){
            newIns(Operation.PUSH, (int) value.charAt(i));
        }
    }
    private void pushUint(Token token) throws CompileError {
        expect(TokenType.UINT_LITERAL);
        newIns(Operation.PUSH,Long.parseLong((String)token.getValue()));
    }
    private void pushDouble(Token token) throws TokenizeError {
        next();
        //todo
        System.out.println("push "+token.getValue());
    }
    private void pushFun(Token token) throws CompileError{
        String name = (String)token.getValue();
        Functiondef function = program.find(name);
        if(function == null){
            System.out.println("Err");
            if(isStd(name)){
                pushStd(name);
            }
            throw new AnalyzeError(ErrorCode.NotDeclared,token.getStartPos());
        }

        // 申请返回空间
        for(int i=0;i<function.returnSize;i++){
            newIns(Operation.PUSH,0);
        }

        expect(TokenType.IDENT);
        // 传递参数
        expect(TokenType.L_PAREN);
        for(int i=0;i<function.params.size();i++){
            if(i != 0){
                expect(TokenType.COMMA);
            }
            analyseExpr();
        }
        // call
        newIns(Operation.CALL,function.id);

        expect(TokenType.R_PAREN);
    }
    private void pushIdent(Token token) throws CompileError {
        String name = (String) token.getValue();
        SymbolEntry symbol = symboler.findSymbol(name);
        if(symbol == null){
            if(isStd(name)){
                pushStd(name);
                return;
            }else {
                throw new AnalyzeError(ErrorCode.NotDeclared, token.getStartPos());
            }
        }
        if(symbol.type == SymbolType.FUN_NAME){
            pushFun(token);
        }else{
            expect(TokenType.IDENT);
            if(nextIf(TokenType.ASSIGN) != null) {
                pushVar(token,false);
                analyseExpr();
                newIns(Operation.STORE64);
            }else {
                pushVar(token,true);
            }
        }
    }

    // 变量，不是函数的变量
    private void pushVar(Token token,boolean needToLoad) throws CompileError {
        SymbolEntry symbol = symboler.findSymbol((String) token.getValue());
        if(symbol == null){
            throw new AnalyzeError(ErrorCode.NoError,token.getStartPos());
        }
        if (symbol.isGlobal) {
            newIns(Operation.GLOBA, symbol.stackOffset);
        } else if (symbol.isParam) {
            newIns(Operation.ARGA, symbol.stackOffset);
        } else {
            newIns(Operation.LOCA, symbol.stackOffset);
        }
        if(needToLoad){
            newIns(Operation.LOAD64);
        }
    }

    private boolean isCmpOp(TokenType type){
        return type == TokenType.LE || type == TokenType.GE ||
                type == TokenType.GT || type == TokenType.LT ||
                type == TokenType.EQ || type == TokenType.NEQ;
    }

    private SymbolType tokenToSymbolType(Token token) throws CompileError{
        String tokenName = (String)token.getValue();
        if(tokenName.equals("int")){
            return SymbolType.INT_NAME;
        }else if(tokenName.equals("double")){
            return SymbolType.DOUBLE_NAME;
        }else if(tokenName.equals("void")){
            return SymbolType.VOID_NAME;
        }
        throw new AnalyzeError(ErrorCode.InvalidInput,token.getStartPos());
    }

    private boolean isStd(String name){
        String[] stds = new String[]{"getint","getdouble","getchar",
                "putint","putdouble","putchar","putstr","putln"};
        for(String std:stds){
            if(name.equals(std))
                return true;
        }
        return false;
    }
    private void pushStd(String name) throws CompileError {
        expect(TokenType.IDENT);
        if(name.equals("getint")){
            expect(TokenType.L_PAREN);
            expect(TokenType.R_PAREN);
            newIns(Operation.SCAN_I);
        }else if(name.equals("getchar")){
            expect(TokenType.L_PAREN);
            newIns(Operation.SCAN_C);
            expect(TokenType.R_PAREN);
        }else if(name.equals("putint")){
            expect(TokenType.L_PAREN);
            analyseExpr();
            expect(TokenType.R_PAREN);
            newIns(Operation.PRINT_I);
        }else if(name.equals("putstr")){
            expect(TokenType.L_PAREN);

            if(peek().getTokenType() != TokenType.STRING_LITERAL){
                throw new AnalyzeError(ErrorCode.InvalidInput,peekedToken.getStartPos());
            }
            String str = (String) peek().getValue();
            analyseExprItem();
            for(int i=0;i<str.length();i++){
                newIns(Operation.PRINT_C);
            }

            expect(TokenType.R_PAREN);
        }else if(name.equals("putchar")){
            expect(TokenType.L_PAREN);
            analyseExpr();
            newIns(Operation.PRINT_C);
            expect(TokenType.R_PAREN);
        }else if(name.equals("putln")){
            expect(TokenType.L_PAREN);
            newIns(Operation.PUSH,(int)'\n');
            newIns(Operation.PRINT_C);
            expect(TokenType.R_PAREN);
        }
    }
    
    private void newIns(Operation opt, Integer x){
        instructions.add(new Instruction(opt,(long)x));
    }
    private void newIns(Operation opt, Long x){
        instructions.add(new Instruction(opt,(long)x));
    }
    private void newIns(Operation opt){
        instructions.add(new Instruction(opt));
    }
//
//
//    /**
//     * 常表达式 -> 符号? 无符号整数
//     * @throws CompileError
//     */
//    private int analyseConstantExpression() throws CompileError {
//        boolean negative = false;
//        if (nextIf(TokenType.Plus) != null) {
//            negative = false;
//        } else if (nextIf(TokenType.Minus) != null) {
//            negative = true;
//        }
//
//        var token = expect(TokenType.Uint);
//
//        int value = (int) token.getValue();
//        if (negative) {
//            value = -value;
//        }
//
//        return value;
//    }
//
//    /**
//     * 赋值语句 -> 标识符 '=' 表达式 ';'
//     * @throws CompileError
//     */
//    private void analyseAssignmentStatement() throws CompileError {
//        // 分析这个语句
//
//        // 标识符
//        Token ident = expect(TokenType.Ident);
//        String name = (String)ident.getValue();
//        SymbolEntry symbol = symbolTable.get(name);
//        if (symbol == null) {
//            // 没有这个标识符
//            throw new AnalyzeError(ErrorCode.NotDeclared, /* 当前位置 */ null);
//        } else if (symbol.isConstant) {
//            // 标识符是常量
//            throw new AnalyzeError(ErrorCode.AssignToConstant, /* 当前位置 */ null);
//        }
//
//        // 标识符是变量
//
//        expect(TokenType.Equal);
//
//        // 计算表达式的值
//        analyseExpression();
//
//        expect(TokenType.Semicolon);
//
//        // 设置符号已初始化
//        initializeSymbol(name, null);
//
//        // 把结果保存
//        var offset = getOffset(name, null);
//        newIns(Operation.STO, offset));
//    }
//
//    private void analysePrint() throws CompileError {
//        // 输出语句 -> 'print' '(' 表达式 ')' ';'
//
//        expect(TokenType.Print);
//        expect(TokenType.LParen);
//
//        analyseExpression();
//
//        expect(TokenType.RParen);
//        expect(TokenType.Semicolon);
//
//        newIns(Operation.WRT));
//    }
//
//    /**
//     * 表达式 -> 项 (加法运算符 项)*
//     * @throws CompileError
//     */
//    private void analyseExpression() throws CompileError {
//        // 项
//        analyseItem();
//
//        while (true) {
//            // 预读可能是运算符的 token
//            var op = peek();
//            if (op.getTokenType() != TokenType.Plus && op.getTokenType() != TokenType.Minus) {
//                break;
//            }
//
//            // 运算符
//            next();
//
//            // 项
//            analyseItem();
//
//            // 生成代码
//            if (op.getTokenType() == TokenType.Plus) {
//                newIns(Operation.ADD));
//            } else if (op.getTokenType() == TokenType.Minus) {
//                newIns(Operation.SUB));
//            }
//        }
//    }
//
//    /**
//     * 项 -> 因子 (乘法运算符 因子)*
//     * @throws CompileError
//     */
//    private void analyseItem() throws CompileError {
//
//        // 因子
//        analyseFactor();
//
//        while (true) {
//            // 预读可能是运算符的 token
//            Token op = peek();
//            if (op.getTokenType() != TokenType.Mult && op.getTokenType() != TokenType.Div) {
//                break;
//            }
//
//            // 运算符
//            next();
//            // 因子
//            analyseFactor();
//
//            // 生成代码
//            if (op.getTokenType() == TokenType.Mult) {
//                newIns(Operation.MUL));
//            } else if (op.getTokenType() == TokenType.Div) {
//                newIns(Operation.DIV));
//            }
//        }
//    }
//
//    /**
//     * 因子 -> 符号? (标识符 | 无符号整数 | '(' 表达式 ')')
//     * @throws CompileError
//     */
//    private void analyseFactor() throws CompileError {
//
//        boolean negate;
//        if (nextIf(TokenType.Minus) != null) {
//            negate = true;
//            // 计算结果需要被 0 减
//            newIns(Operation.LIT, 0));
//        } else {
//            nextIf(TokenType.Plus);
//            negate = false;
//        }
//
//        if (check(TokenType.Ident)) {
//            // 是标识符
//
//            // 加载标识符的值
//            String name = (String) next().getValue();
//            var symbol = symbolTable.get(name);
//            if (symbol == null) {
//                // 没有这个标识符
//                throw new AnalyzeError(ErrorCode.NotDeclared, /* 当前位置 */ null);
//            } else if (!symbol.isInitialized) {
//                // 标识符没初始化
//                throw new AnalyzeError(ErrorCode.NotInitialized, /* 当前位置 */ null);
//            }
//            var offset = getOffset(name, null);
//            newIns(Operation.LOD, offset));
//        } else if (check(TokenType.Uint)) {
//            // 是整数
//            // 载整数值
//            int value = (int)next().getValue();
//            newIns(Operation.LIT, value));
//        } else if (check(TokenType.LParen)) {
//            // 是表达式
//            next();
//            analyseExpression();
//            expect(TokenType.RParen);
//        } else {
//            // 都不是，摸了
//            throw new ExpectedTokenError(List.of(TokenType.Ident, TokenType.Uint, TokenType.LParen), next());
//        }
//
//        if (negate) {
//            newIns(Operation.SUB));
//        }
//    }
}
