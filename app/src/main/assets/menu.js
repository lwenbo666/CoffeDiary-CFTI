    // ==================== 全局变量 ====================
    let recipes = [];
    let editingId = null;
    let tempImageData = null;
    let selectedCategory = '美式';
    let selectedRoast = '中深烘';
    let selectedUnit = 'ml';
    let currentFilter = 'all';
    let currentPage = 0;
    let totalPages = 0;

    // ==================== 初始化 ====================
    function init() {
        loadRecipes();
    }

    function loadRecipes() {
        try {
            const data = localStorage.getItem('coffeeRecipes');
            recipes = data ? JSON.parse(data) : [];
        } catch (e) {
            recipes = [];
        }
        renderRecipes();
    }

    function saveRecipes() {
        try {
            const json = JSON.stringify(recipes);
            localStorage.setItem('coffeeRecipes', json);
        } catch (e) {
            // localStorage 容量不足，尝试移除图片后再保存
            if (e.name === 'QuotaExceededError' || e.code === 22) {
                const recipesWithoutImages = recipes.map(r => ({
                    ...r,
                    image: r.image && r.image.length > 50000 ? '' : r.image
                }));
                try {
                    localStorage.setItem('coffeeRecipes', JSON.stringify(recipesWithoutImages));
                    recipes = recipesWithoutImages;
                } catch (e2) {
                    showToast('存储空间不足，无法保存');
                }
            }
        }
    }

    // ==================== 渲染 ====================
    function renderRecipes() {
        const container = document.getElementById('recipesContainer');

        // 按分类筛选
        const filtered = currentFilter === 'all'
            ? recipes
            : recipes.filter(r => r.category === currentFilter);

        document.getElementById('recipeCount').textContent = filtered.length + '/' + recipes.length + ' 个配方';

        if (filtered.length === 0) {
            container.className = 'recipes-container empty';
            container.innerHTML = `
                <div class="empty-state">
                    <div class="empty-state-icon">📖</div>
                    <div class="empty-state-title">${currentFilter === 'all' ? '还没有咖啡配方' : currentFilter + '系暂无配方'}</div>
                    <div class="empty-state-desc">${currentFilter === 'all' ? '点击右下角 + 号<br>添加你的第一份咖啡配方' : '切换分类或添加新配方'}</div>
                </div>
            `;
            updateIndicators(0);
            return;
        }

        // 每页 2 个配方，分组
        const ITEMS_PER_PAGE = 2;
        const groups = [];
        for (let i = 0; i < filtered.length; i += ITEMS_PER_PAGE) {
            groups.push(filtered.slice(i, i + ITEMS_PER_PAGE));
        }

        container.className = 'recipes-container';
        container.innerHTML = groups.map((group, pageIndex) => `
            <div class="recipe-page" data-page="${pageIndex}">
                ${group.map(r => `
                    <div class="recipe-card-small" onclick="openDetailModal(${recipes.indexOf(r)})">
                        <div class="recipe-image-small">
                            ${r.image ? `<img src="${r.image}" alt="${r.name}">` : '<i class="fas fa-mug-hot"></i>'}
                        </div>
                        <div class="recipe-info-small">
                            <div class="recipe-name-small">${escapeHtml(r.name)}</div>
                            <div class="recipe-materials-small">${getMaterialSummary(r)}</div>
                        </div>
                    </div>
                `).join('')}
            </div>
        `).join('');

        totalPages = groups.length;
        currentPage = 0;
        updateIndicators(groups.length);
        container.scrollLeft = 0;
    }

    function getMaterialSummary(r) {
        const parts = [];
        if (r.espresso) parts.push('☕' + r.espresso + (r.espressoUnit || 'ml'));
        if (r.ice) parts.push('🧊' + r.ice + 'g');
        if (r.syrup) parts.push('🍯' + r.syrup);
        if (r.other) parts.push('🥛' + r.other.split('\n')[0]);
        return parts.join(' ') || escapeHtml(r.category || '');
    }

    function updateIndicators(count) {
        const indicatorEl = document.getElementById('pageIndicators');
        if (count <= 1) {
            indicatorEl.innerHTML = '';
            return;
        }
        indicatorEl.innerHTML = Array.from({ length: count }, (_, i) => `
            <div class="page-dot${i === currentPage ? ' active' : ''}"></div>
        `).join('');
    }

    function updateActiveIndicator() {
        const dots = document.querySelectorAll('.page-dot');
        dots.forEach((dot, i) => {
            dot.classList.toggle('active', i === currentPage);
        });
    }

    function goToPage(index) {
        const container = document.getElementById('recipesContainer');
        if (container.classList.contains('empty')) return;
        const pages = container.children;
        if (index >= 0 && index < pages.length) {
            currentPage = index;
            pages[index].scrollIntoView({ behavior: 'smooth', inline: 'center' });
            updateActiveIndicator();
        }
    }

    function escapeHtml(text) {
        if (!text) return '';
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // ==================== 图片选择 ====================
    function pickImage() {
        if (typeof window.AndroidBridge !== 'undefined' && window.AndroidBridge.openGallery) {
            window.AndroidBridge.openGallery();
        } else {
            // 降级：使用 file input（桌面调试）
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = 'image/*';
            input.onchange = function(e) {
                const file = e.target.files[0];
                if (!file) return;
                const reader = new FileReader();
                reader.onload = function(ev) {
                    tempImageData = ev.target.result;
                    updateImagePicker();
                };
                reader.readAsDataURL(file);
            };
            input.click();
        }
    }

    // AndroidBridge 回调
    function onGalleryImage(base64) {
        tempImageData = 'data:image/jpeg;base64,' + base64;
        updateImagePicker();
    }

    function updateImagePicker() {
        const picker = document.getElementById('imagePicker');
        if (tempImageData) {
            picker.classList.add('has-image');
            picker.innerHTML = `
                <img src="${tempImageData}" alt="preview">
                <i class="fas fa-camera image-picker-icon"></i>
                <span class="image-picker-text">点击更换图片</span>
            `;
        } else {
            picker.classList.remove('has-image');
            picker.innerHTML = `
                <i class="fas fa-camera image-picker-icon"></i>
                <span class="image-picker-text">点击从相册选择</span>
            `;
        }
    }

    // ==================== 分段选择 ====================
    function selectCategory(el) {
        document.querySelectorAll('#categoryGroup .segment-btn').forEach(b => b.classList.remove('selected'));
        el.classList.add('selected');
        selectedCategory = el.dataset.value;
    }

    function selectRoast(el) {
        document.querySelectorAll('#roastGroup .segment-btn').forEach(b => b.classList.remove('selected'));
        el.classList.add('selected');
        selectedRoast = el.dataset.value;
    }

    function selectUnit(el) {
        document.querySelectorAll('#espressoUnitGroup .unit-btn').forEach(b => b.classList.remove('selected'));
        el.classList.add('selected');
        selectedUnit = el.dataset.unit;
    }

    // ==================== 添加/编辑模态框 ====================
    function openAddModal() {
        editingId = null;
        tempImageData = null;
        selectedCategory = '美式';
        selectedRoast = '中深烘';
        selectedUnit = 'ml';

        document.getElementById('editModalTitle').textContent = '添加咖啡配方';
        document.getElementById('inputName').value = '';
        document.getElementById('inputEspresso').value = '';
        document.getElementById('inputIce').value = '';
        document.getElementById('inputSyrup').value = '';
        document.getElementById('inputOther').value = '';
        document.getElementById('inputSteps').value = '';
        document.getElementById('btnDelete').style.display = 'none';

        resetSegmentGroups();
        updateImagePicker();
        showModal('editModal');
    }

    function openEditModal(index) {
        const recipe = recipes[index];
        editingId = index;
        tempImageData = recipe.image || null;
        selectedCategory = recipe.category || '美式';
        selectedRoast = recipe.roast || '中深烘';
        selectedUnit = recipe.espressoUnit || 'ml';

        document.getElementById('editModalTitle').textContent = '编辑咖啡配方';
        document.getElementById('inputName').value = recipe.name || '';
        document.getElementById('inputEspresso').value = recipe.espresso || '';
        document.getElementById('inputIce').value = recipe.ice || '';
        document.getElementById('inputSyrup').value = recipe.syrup || '';
        document.getElementById('inputOther').value = recipe.other || '';
        document.getElementById('inputSteps').value = recipe.steps || '';
        document.getElementById('btnDelete').style.display = 'block';

        resetSegmentGroups();
        updateImagePicker();
        showModal('editModal');
    }

    function resetSegmentGroups() {
        document.querySelectorAll('#categoryGroup .segment-btn').forEach(b => {
            b.classList.toggle('selected', b.dataset.value === selectedCategory);
        });
        document.querySelectorAll('#roastGroup .segment-btn').forEach(b => {
            b.classList.toggle('selected', b.dataset.value === selectedRoast);
        });
        document.querySelectorAll('#espressoUnitGroup .unit-btn').forEach(b => {
            b.classList.toggle('selected', b.dataset.unit === selectedUnit);
        });
    }

    function closeEditModal() {
        hideModal('editModal');
    }

    function saveRecipe() {
        const name = document.getElementById('inputName').value.trim();
        if (!name) {
            showToast('请输入咖啡名称');
            return;
        }

        const recipe = {
            id: editingId !== null ? recipes[editingId].id : Date.now(),
            name: name,
            image: tempImageData || '',
            category: selectedCategory,
            roast: selectedRoast,
            espresso: document.getElementById('inputEspresso').value,
            espressoUnit: selectedUnit,
            ice: document.getElementById('inputIce').value,
            syrup: document.getElementById('inputSyrup').value.trim(),
            other: document.getElementById('inputOther').value.trim(),
            steps: document.getElementById('inputSteps').value.trim(),
            createdAt: editingId !== null ? recipes[editingId].createdAt : new Date().toISOString().split('T')[0]
        };

        if (editingId !== null) {
            recipes[editingId] = recipe;
        } else {
            recipes.unshift(recipe);
        }

        saveRecipes();
        hideModal('editModal');
        renderRecipes();
        showToast(editingId !== null ? '配方已更新' : '配方已添加');
    }

    let confirmCallback = null;

    function showConfirmModal(title, msg, onConfirm) {
        document.getElementById('confirmTitle').textContent = title;
        document.getElementById('confirmMsg').textContent = msg;
        confirmCallback = onConfirm;
        const modal = document.getElementById('confirmModal');
        modal.classList.add('show');
    }

    function hideConfirmModal() {
        document.getElementById('confirmModal').classList.remove('show');
        confirmCallback = null;
    }

    function deleteCurrentRecipe() {
        if (editingId === null) return;
        const name = recipes[editingId].name;
        showConfirmModal('确认删除', '确定要删除「' + name + '」吗？', () => {
            recipes.splice(editingId, 1);
            saveRecipes();
            hideModal('editModal');
            renderRecipes();
            showToast('配方已删除');
            hideConfirmModal();
        });
    }

    // ==================== 详情模态框 ====================
    function openDetailModal(index) {
        const recipe = recipes[index];
        const content = document.getElementById('detailContent');

        // 拼配参数行
        let paramsHtml = '';
        if (recipe.roast) paramsHtml += `<span class="recipe-tag">🫘 ${escapeHtml(recipe.roast)}</span>`;
        if (recipe.espresso) paramsHtml += `<span class="recipe-tag">☕ ${escapeHtml(recipe.espresso)}${escapeHtml(recipe.espressoUnit || 'ml')}</span>`;
        if (recipe.ice) paramsHtml += `<span class="recipe-tag ice">🧊 ${escapeHtml(recipe.ice)}g</span>`;
        if (recipe.syrup) paramsHtml += `<span class="recipe-tag hot">🍯 ${escapeHtml(recipe.syrup)}</span>`;

        content.innerHTML = `
            <div class="modal-handle"></div>
            ${recipe.image ? `<img class="detail-image" src="${recipe.image}" alt="${recipe.name}">` : `
            <div class="detail-image" style="display:flex;align-items:center;justify-content:center;">
                <i class="fas fa-mug-hot" style="font-size:48px;color:#BCAAA4;"></i>
            </div>`}
            <div class="detail-name">${escapeHtml(recipe.name)}</div>
            <div class="detail-tags">
                <span class="recipe-tag ${recipe.category === '拿铁' ? '' : 'hot'}">${escapeHtml(recipe.category || '美式')}</span>
                ${paramsHtml}
            </div>
            ${recipe.other ? `
            <div class="detail-section">
                <div class="detail-section-title">🥛 其他配料</div>
                <div class="detail-section-content">${escapeHtml(recipe.other)}</div>
            </div>` : ''}
            ${recipe.steps ? `
            <div class="detail-section">
                <div class="detail-section-title">📝 制作步骤</div>
                <div class="detail-section-content">${escapeHtml(recipe.steps)}</div>
            </div>` : ''}
            <div class="detail-meta">创建于 ${recipe.createdAt}</div>
            <div class="btn-row">
                <button class="btn-cancel" onclick="hideModal('detailModal')">关闭</button>
                <button class="btn-save" onclick="hideModal('detailModal');openEditModal(${index})">编辑</button>
            </div>
        `;

        showModal('detailModal');
    }

    // ==================== 模态框工具 ====================
    function showModal(id) {
        document.getElementById(id).classList.add('show');
        document.body.style.overflow = 'hidden';
    }

    function hideModal(id) {
        document.getElementById(id).classList.remove('show');
        document.body.style.overflow = 'hidden';
    }

    // ==================== 返回 ====================
    function goBack() {
        const cover = document.getElementById('transitionCover');
        cover.classList.add('active');
        setTimeout(() => {
            if (typeof window.AndroidBridge !== 'undefined' && window.AndroidBridge.loadPage) {
                window.AndroidBridge.loadPage('main');
            } else {
                window.history.back();
            }
        }, 200);
    }

    // ==================== Toast ====================
    let toastTimer;
    function showToast(msg) {
        const toast = document.getElementById('toast');
        toast.textContent = msg;
        toast.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => {
            toast.classList.remove('show');
        }, 2000);
    }

    // ==================== 键盘遮挡输入框处理 ====================
    function setupKeyboardHandling() {
        const modal = document.getElementById('editModal');
        const modalContent = modal.querySelector('.modal-content');
        const focusable = modal.querySelectorAll('.form-input, .form-textarea');

        focusable.forEach(function(el) {
            el.addEventListener('focus', function() {
                // 延迟等键盘弹起后再滚动
                setTimeout(function() {
                    // 获取元素在 modal-content 中的相对位置并滚动
                    var rect = el.getBoundingClientRect();
                    var modalRect = modalContent.getBoundingClientRect();
                    var offset = rect.top - modalRect.top - 100;
                    if (offset > 0) {
                        modalContent.scrollBy({ top: offset, behavior: 'smooth' });
                    }
                }, 350);
            });
        });

        // 监听窗口 resize（键盘收起时恢复滚动）
        var lastHeight = window.innerHeight;
        window.addEventListener('resize', function() {
            var newHeight = window.innerHeight;
            // 键盘收起时（视图变高），保持滚动位置
            if (newHeight > lastHeight && modal.classList.contains('show')) {
                // 键盘收起不做额外处理，保持当前滚动即可
            }
            lastHeight = newHeight;
        });
    }

    // ==================== 启动 ====================
    document.addEventListener('DOMContentLoaded', () => {
        init();

        // 筛选 Tab 点击事件
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', function() {
                document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('tab-active'));
                this.classList.add('tab-active');
                currentFilter = this.dataset.filter;
                renderRecipes();
            });
        });

        // 翻页滚动监听
        const container = document.getElementById('recipesContainer');
        let scrollTimeout;
        container.addEventListener('scroll', () => {
            clearTimeout(scrollTimeout);
            scrollTimeout = setTimeout(() => {
                const pageWidth = container.clientWidth;
                if (pageWidth <= 0) return;
                const newPage = Math.round(container.scrollLeft / pageWidth);
                if (newPage !== currentPage && newPage >= 0 && newPage < totalPages) {
                    currentPage = newPage;
                    updateActiveIndicator();
                }
            }, 50);
        }, { passive: true });

        // 触摸滑动检测
        let touchStartX = 0;
        container.addEventListener('touchstart', (e) => {
            touchStartX = e.touches[0].clientX;
        }, { passive: true });

        container.addEventListener('touchend', (e) => {
            const diffX = touchStartX - e.changedTouches[0].clientX;
            // 滑动超过 50px 才触发翻页
            if (Math.abs(diffX) > 50) {
                const container = document.getElementById('recipesContainer');
                const pageWidth = container.clientWidth;
                const targetPage = Math.round(container.scrollLeft / pageWidth);
                if (targetPage >= 0 && targetPage < totalPages) {
                    currentPage = targetPage;
                    goToPage(currentPage);
                    updateActiveIndicator();
                }
            }
        }, { passive: true });

        // ==================== 键盘遮挡输入框处理 ====================
        setupKeyboardHandling();

        // 页面加载完成后显示
        requestAnimationFrame(() => {
            document.body.classList.add('loaded');
            setTimeout(() => {
                const cover = document.getElementById('transitionCover');
                if (cover) cover.classList.remove('active');
            }, 300);
        });
    });

    // ==================== 点击模态框遮罩关闭 ====================
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', function(e) {
            if (e.target === this) {
                hideModal(this.id);
            }
        });
    });

    // 确认弹窗 — 确定按钮
    document.getElementById('confirmBtnAction').addEventListener('click', () => {
        if (confirmCallback) confirmCallback();
    });

    // 确认弹窗 — 点击遮罩关闭
    document.getElementById('confirmModal').addEventListener('click', function(e) {
        if (e.target === this) hideConfirmModal();
    });
