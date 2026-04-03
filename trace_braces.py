import sys

def find_unbalanced_brace(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    stack = []
    map_screen_start = -1
    for i, line in enumerate(lines):
        if "fun MapScreen(" in line:
            map_screen_start = i + 1
        
        for j, char in enumerate(line):
            if char == '{':
                stack.append((i + 1, j + 1))
            elif char == '}':
                if not stack:
                    print(f"Extra closing brace at Line {i+1}, Col {j+1}")
                else:
                    start_line, start_col = stack.pop()
                    if start_line == map_screen_start:
                         print(f"MapScreen (Starting at Line {map_screen_start}) CLOSES early at Line {i+1}")
    
    if stack:
        print(f"Unclosed braces: {len(stack)}")
        for line, col in stack[-5:]:
            print(f"  Unclosed opening brace at Line {line}")

if __name__ == "__main__":
    find_unbalanced_brace(sys.argv[1])
