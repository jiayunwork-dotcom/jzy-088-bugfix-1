package com.example.bem.induction;

/**
 * 叶素诱导因子迭代无法在限定轮数内收敛时抛出。
 * 调用方必须把它作为核算失败处理，禁止把发散/未收敛的值当成结果返回。
 */
public class NonConvergentException extends RuntimeException {

    public NonConvergentException(String message) {
        super(message);
    }
}
