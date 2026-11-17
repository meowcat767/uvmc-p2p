# Contributing

### Coding Styles
#### General
- Try to limit lines of code to a maximum of 100 characters.
    - Note that this does not mean you should try and use all 100 characters every time you have the chance. Typically with well formatted code, you normally shouldn't hit a line count of anything over 80 or 90 characters.
- The indentation style we use is 2 spaces per level.
- The opening brace for classes, records, enums and exceptions go on the current line, like so:
```java
protected class PickALeader() implements Leader extends IBaseConnectivity() {
    System.out.println("Picking a leader...");
}
```
- Don't collapse single line conditional or loop bodies onto the same line as its header. Put it on the next line.
  - Yes:

    ```java
    if (condition)
      return 0;

    while (var != 0)
      var--;
    ```
  - No:
    ```java
    if (condition) return 0;

    while (var != 0) var--;
    ```
## Naming
- All variable, class, enum, function, record and exception names should be in upper CamelCase. If the name contains an abbreviation uppercase it.
  - `class SomeClassName`
  - `enum IPCCommandType`

## JavaDocs
- Public APIs: Every public and protected class, interface, enum, method, and field must have a Javadoc.
- Side Effects: Methods that modify global state, trigger asynchronous events, or take a long time to run must explicitly call out these behaviors.
- Nullability: Always specify whether parameters or return values can be null (e.g., "May return {@code null} if the key is not found.").
- Deprecation: If code is marked with @Deprecated, the Javadoc must explain why it was deprecated and what contributors should use instead.
- Self-Explanatory Code: If a method is short, private, and reads like a sentence (e.g., `private boolean isUserLoggedIn()`), a Javadoc is unnecessary.
- Required Tags: For any documented method, contributors must include:
  - @param for every input parameter.
	- @return if the method return type is not void.
  - @throws or @exception for any checked or common unchecked exceptions.




## Generative AI
We accept contributions from humans, not from AI agents. We expect you to understand and take responsibility for code submitted in your name. If a non-trivial part of your contribution is generated using AI, you need to disclose which parts are generated.

### AI Training
This codebase is not permitted to be used in **any** datasets for generative AI training.
