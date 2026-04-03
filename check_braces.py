import sys

def check_braces(filename):
    count = 0
    in_string = False
    in_char = False
    in_line_comment = False
    in_block_comment = False
    template_depth = 0
    
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()
    
    i = 0
    line_num = 1
    prev_depth = 0
    
    while i < len(content):
        char = content[i]
        next_char = content[i+1] if i + 1 < len(content) else ''
        
        if char == '\n':
            line_num += 1
            in_line_comment = False
            i += 1
            continue
        if in_line_comment:
            i += 1
            continue
        if in_block_comment:
            if char == '*' and next_char == '/':
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_string:
            if char == '\\':
                i += 2
                continue
            if char == '"':
                in_string = False
                i += 1
                continue
            if char == '$' and next_char == '{':
                count += 1
                template_depth += 1
                i += 2
                continue
            if char == '}' and template_depth > 0:
                count -= 1
                template_depth -= 1
                i += 1
                continue
            i += 1
            continue
        if in_char:
            if char == '\\':
                i += 2
                continue
            if char == "'":
                in_char = False
            i += 1
            continue
        if char == '/' and next_char == '/':
            in_line_comment = True
            i += 2
            continue
        if char == '/' and next_char == '*':
            in_block_comment = True
            i += 2
            continue
        if char == '"':
            in_string = True
            i += 1
            continue
        if char == "'":
            in_char = True
            i += 1
            continue
            
        if char == '{':
            count += 1
            print(f"Line {line_num}: Open '{{' depth={count}")
        elif char == '}':
            count -= 1
            if count < 0:
                print(f"UNMATCHED CLOSE at line {line_num}!")
                return
            print(f"Line {line_num}: Close '}}' depth={count}")
        
        i += 1

    print(f"\nTotal: {line_num} lines, Final depth: {count}")
    print("PERFECTLY BALANCED" if count == 0 else f"MISSING {count}" if count > 0 else f"EXTRA {abs(count)}")

if __name__ == "__main__":
    check_braces(sys.argv[1])
