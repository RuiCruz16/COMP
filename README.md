# Compiler Project

## jmm Compiler Optimizations
 Here we listed the optimizations we have implemented in our jmm compiler.
 
### 1. Register Allocation (`-r=<n>`)

We implemented all the register allocation strategies supported by the compiler:

- **n = 0:** The compiler attempts to minimize the number of JVM local variables by reusing local variables wherever possible.

- **n = 1:** The compiler uses the same number of local variables as in the original OLLIR code, preserving the variable mapping.

- **n >= 1:** The compiler limits the number of local variables to `<n>` (≥ 1). It includes:

    - Mapping between method-level variables and JVM local variables.

    - Lifetime analysis of variables.

    - Graph coloring-based register allocation.

These approaches are correctly integrated and selectable via the `-r=<n>` option.

### 2. Other Optimizations (`-o`)

With the `-o` flag, we implemented the following AST-level optimizations:

- **Constant Propagation:**

    - Detects variables with constant values and replaces their usages with the constant.

    - Reduces the number of required JVM variables and simplifies expressions.

- **Constant Folding:**

    - Evaluates constant expressions at compile time (e.g., `10 + 5` becomes `15`).

    - Reduces runtime computation and further simplifies the AST.
