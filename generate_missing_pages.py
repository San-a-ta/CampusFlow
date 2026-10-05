import os

base_dir = r"C:\Users\Dell\.gemini\antigravity\scratch\CampusFlow\src\main\resources\templates\admin"
pages = {
    "attendance": ("Attendance", "Monitor campus-wide attendance records"),
    "assignments": ("Assignments", "Overview of course assignments and submissions"),
    "documents": ("Documents", "Manage study materials and institutional documents"),
    "events": ("Events", "Campus events and schedules")
}

for page, (title, desc) in pages.items():
    content = f"""<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{{admin/fragments :: head('{title}')}}">
</head>
<body>
    <div class="wrapper">
        <div th:replace="~{{admin/fragments :: sidebar('{page}')}}"></div>
        
        <div class="main-panel">
            <div th:replace="~{{admin/fragments :: navbar('{title}')}}"></div>

            <div class="content-padding">
                <div class="d-flex justify-content-between align-items-center mb-4">
                    <div>
                        <h2 class="page-title">{title}</h2>
                        <p class="text-muted mb-0">{desc}</p>
                    </div>
                    <button class="btn btn-primary shadow-sm"><i class="bi bi-plus-lg me-1"></i> Add New</button>
                </div>
                
                <div class="card">
                    <div class="card-body p-0">
                        <div class="text-center py-5 text-muted">
                            <i class="bi bi-tools fs-1 d-block mb-3 opacity-50"></i>
                            <h5 class="fw-semibold">{title} module under construction</h5>
                            <p class="mb-0">This feature is currently being integrated.</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</body>
</html>"""
    with open(os.path.join(base_dir, f"{page}.html"), "w", encoding='utf-8') as f:
        f.write(content)

print("Pages created successfully.")
