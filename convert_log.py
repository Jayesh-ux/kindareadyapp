import os

log_path = r'c:\Users\AVERLON ENTERPRISES\OneDrive\Desktop\Jayesh\kindareadyapp\kindareadyapp\build_output_antigravity.txt'
out_path = r'c:\Users\AVERLON ENTERPRISES\OneDrive\Desktop\Jayesh\kindareadyapp\kindareadyapp\build_output_utf8.txt'

if os.path.exists(log_path):
    with open(log_path, 'rb') as f:
        content = f.read().decode('utf-16')
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(content)
