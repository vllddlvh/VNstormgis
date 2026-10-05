module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      [
        'feat',     // Tính năng mới
        'fix',      // Sửa lỗi
        'docs',     // Tài liệu
        'style',    // Định dạng code
        'refactor', // Tái cấu trúc
        'perf',     // Tối ưu hiệu năng
        'test',     // Kiểm thử
        'chore',    // Công việc bảo trì / build / config
        'ci',       // Pipeline CI/CD
        'revert'    // Revert commit trước
      ]
    ],
    'type-case': [2, 'always', 'lower-case'],
    'scope-case': [2, 'always', 'lower-case'],
    'subject-case': [0], // Cho phép viết tự nhiên tiếng Anh hoặc tiếng Việt
    'subject-empty': [2, 'never'],
    'type-empty': [2, 'never'],
    'header-max-length': [2, 'always', 120]
  }
};
