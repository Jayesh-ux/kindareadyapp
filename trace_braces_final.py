import sys

def find_unbalanced_brace(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    stack = []
    for i, line in enumerate(lines):
        line_num = i + 1
        for j, char in enumerate(line):
            if char == '{':
                stack.append(line_num)
            elif char == '}':
                if not stack:
                    print(f"ERROR: Extra closing brace at Line {line_num}")
                else:
                    stack.pop()
    
    if stack:
        print(f"ERROR: {len(stack)} unclosed opening braces. Last 5 were at lines:")
        for l in stack[-5:]:
            print(f"  Line {l}")
    else:
        print("Braces are BALANCED (but check nesting logic)")

if __name__ == "__main__":
    find_unbalanced_brace(sys.argv[1])
