(() => {
  // DOM refs
  const fileInput = document.getElementById('fileInput');
  const originalInfo = document.getElementById('originalInfo');
  const originalPreview = document.getElementById('originalPreview');
  const resizedPreview = document.getElementById('resizedPreview');
  const widthInput = document.getElementById('widthInput');
  const heightInput = document.getElementById('heightInput');
  const aspectCheckbox = document.getElementById('aspectCheckbox');
  const resizeBtn = document.getElementById('resizeBtn');
  const formatSelect = document.getElementById('formatSelect');
  const qualityLabel = document.getElementById('qualityLabel');
  const qualitySlider = document.getElementById('qualitySlider');
  const downloadBtn = document.getElementById('downloadBtn');
  const actionStatus = document.getElementById('actionStatus');
  const pixelEquivalent = document.getElementById('pixelEquivalent');
  const dpiLabel = document.getElementById('dpiLabel');
  const dpiInput = document.getElementById('dpiInput');
  const unitRadios = document.getElementsByName('unit');
  const modeRadios = document.getElementsByName('resizeMode');
  const dimensionControls = document.getElementById('dimensionControls');
  const filesizeControls = document.getElementById('filesizeControls');
  const targetKBInput = document.getElementById('targetKB');
  const compressBtn = document.getElementById('compressBtn');

  let originalImage = null;
  let originalWidth = 0, originalHeight = 0;
  let aspectRatio = 1;
  let currentPxW = 800, currentPxH = 600;
  let resizedBlob = null;

  // ---- helpers ----
  function updateQualityUI() {
    const fmt = formatSelect.value;
    const show = (fmt === 'image/jpeg' || fmt === 'image/webp');
    qualityLabel.classList.toggle('hidden', !show);
    qualitySlider.classList.toggle('hidden', !show);
  }

  function getDPI() { return parseInt(dpiInput.value) || 96; }
  function cmToPx(cm) { return cm * getDPI() / 2.54; }
  function pxToCm(px) { return px * 2.54 / getDPI(); }

  function getCurrentUnit() {
    return [...unitRadios].find(r => r.checked)?.value || 'px';
  }

  function setDisplayedDimensions(pxW, pxH) {
    const unit = getCurrentUnit();
    if (unit === 'px') {
      widthInput.value = Math.round(pxW);
      heightInput.value = Math.round(pxH);
    } else {
      widthInput.value = pxToCm(pxW).toFixed(2);
      heightInput.value = pxToCm(pxH).toFixed(2);
    }
    updatePixelEquivalent();
  }

  function getEnteredDimensions() {
    const unit = getCurrentUnit();
    const rawW = parseFloat(widthInput.value);
    const rawH = parseFloat(heightInput.value);
    if (isNaN(rawW) || isNaN(rawH)) return null;
    if (unit === 'px') return { w: Math.round(rawW), h: Math.round(rawH) };
    return { w: Math.round(cmToPx(rawW)), h: Math.round(cmToPx(rawH)) };
  }

  function syncFromWidth() {
    if (!aspectCheckbox.checked || !originalImage) return;
    const dim = getEnteredDimensions();
    if (!dim) return;
    const newW = dim.w;
    const newH = Math.round(newW / aspectRatio);
    currentPxW = newW;
    currentPxH = newH;
    setDisplayedDimensions(currentPxW, currentPxH);
  }

  function syncFromHeight() {
    if (!aspectCheckbox.checked || !originalImage) return;
    const dim = getEnteredDimensions();
    if (!dim) return;
    const newH = dim.h;
    const newW = Math.round(newH * aspectRatio);
    currentPxW = newW;
    currentPxH = newH;
    setDisplayedDimensions(currentPxW, currentPxH);
  }

  function updatePixelEquivalent() {
    if (!originalImage || getCurrentUnit() !== 'cm') {
      pixelEquivalent.textContent = '';
      return;
    }
    pixelEquivalent.textContent = `≈ ${currentPxW} × ${currentPxH} pixels at ${getDPI()} DPI`;
  }

  function updateModeUI() {
    const mode = [...modeRadios].find(r => r.checked)?.value;
    dimensionControls.classList.toggle('hidden', mode !== 'dimensions');
    filesizeControls.classList.toggle('hidden', mode !== 'filesize');
  }

  // ---- Load image ----
  fileInput.addEventListener('change', function(e) {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = function(ev) {
      const img = new Image();
      img.onload = function() {
        originalImage = img;
        originalWidth = img.naturalWidth;
        originalHeight = img.naturalHeight;
        aspectRatio = originalWidth / originalHeight;
        currentPxW = originalWidth;
        currentPxH = originalHeight;
        setDisplayedDimensions(currentPxW, currentPxH);
        originalInfo.textContent = `Original: ${originalWidth} × ${originalHeight} | ${(file.size/1024).toFixed(1)} KB`;
        originalPreview.innerHTML = '';
        const clone = img.cloneNode();
        clone.style.maxWidth = '100%'; clone.style.maxHeight = '240px';
        originalPreview.appendChild(clone);
        resizedPreview.innerHTML = '<span>Processed</span>';
        resizedBlob = null;
        downloadBtn.disabled = true;
        actionStatus.textContent = 'Image loaded. Choose mode and options.';
      };
      img.src = ev.target.result;
    };
    reader.readAsDataURL(file);
  });

  // ---- Aspect ratio sync ----
  widthInput.addEventListener('input', syncFromWidth);
  heightInput.addEventListener('input', syncFromHeight);
  aspectCheckbox.addEventListener('change', () => {
    if (aspectCheckbox.checked && originalImage) syncFromWidth();
  });

  // ---- Unit / DPI changes ----
  unitRadios.forEach(r => r.addEventListener('change', () => setDisplayedDimensions(currentPxW, currentPxH)));
  dpiInput.addEventListener('input', () => {
    if (getCurrentUnit() === 'cm') setDisplayedDimensions(currentPxW, currentPxH);
  });

  // ---- Mode switch ----
  modeRadios.forEach(r => r.addEventListener('change', updateModeUI));

  // ---- Resize by dimensions ----
  resizeBtn.addEventListener('click', () => {
    if (!originalImage) { actionStatus.textContent = 'Please open an image first.'; return; }
    const dim = getEnteredDimensions();
    if (!dim || dim.w < 1 || dim.h < 1) { actionStatus.textContent = 'Invalid dimensions.'; return; }
    const targetW = dim.w, targetH = dim.h;
    const canvas = document.createElement('canvas');
    canvas.width = targetW; canvas.height = targetH;
    const ctx = canvas.getContext('2d');
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(originalImage, 0, 0, targetW, targetH);
    const previewUrl = canvas.toDataURL('image/png');
    resizedPreview.innerHTML = '';
    const img = new Image();
    img.src = previewUrl;
    img.style.maxWidth = '100%'; img.style.maxHeight = '240px';
    resizedPreview.appendChild(img);
    const fmt = formatSelect.value;
    const q = (fmt === 'image/jpeg' || fmt === 'image/webp') ? parseFloat(qualitySlider.value) : undefined;
    canvas.toBlob(blob => {
      resizedBlob = blob;
      downloadBtn.disabled = false;
      actionStatus.textContent = `Resized to ${targetW}×${targetH} px (${(blob.size/1024).toFixed(1)} KB).`;
    }, fmt, q);
  });

  // ---- Compress to file size (with auto‑downscale) ----
  compressBtn.addEventListener('click', async () => {
    if (!originalImage) { actionStatus.textContent = 'No image.'; return; }
    const targetBytes = parseFloat(targetKBInput.value) * 1024;
    if (isNaN(targetBytes) || targetBytes <= 0) { actionStatus.textContent = 'Invalid target size.'; return; }
    const fmt = formatSelect.value;
    if (fmt === 'image/png') {
      actionStatus.textContent = 'PNG is lossless – cannot target file size. Use JPEG or WebP.';
      return;
    }
    compressBtn.disabled = true;
    actionStatus.textContent = 'Compressing…';

    async function compressAtSize(w, h) {
      const canvas = document.createElement('canvas');
      canvas.width = w; canvas.height = h;
      const ctx = canvas.getContext('2d');
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(originalImage, 0, 0, w, h);
      let lo = 0.1, hi = 1.0, bestBlob = null, bestQ = 0;
      for (let i = 0; i < 12; i++) {
        const mid = (lo + hi) / 2;
        const blob = await new Promise(res => canvas.toBlob(res, fmt, mid));
        if (blob.size <= targetBytes) {
          bestBlob = blob; bestQ = mid; lo = mid;
        } else {
          hi = mid;
        }
      }
      return { blob: bestBlob, quality: bestQ };
    }

    let currentW = originalWidth;
    let currentH = originalHeight;
    const minDim = 1;
    let bestResult = null;

    while (currentW >= minDim && currentH >= minDim) {
      const result = await compressAtSize(currentW, currentH);
      if (result.blob) {
        bestResult = { ...result, w: currentW, h: currentH };
        break;
      }
      currentW = Math.max(minDim, Math.floor(currentW * 0.8));
      currentH = Math.round(currentW / aspectRatio);
      if (currentH < minDim) {
        currentH = minDim;
        currentW = Math.round(currentH * aspectRatio);
      }
    }

    if (bestResult && bestResult.blob) {
      resizedBlob = bestResult.blob;
      const url = URL.createObjectURL(bestResult.blob);
      resizedPreview.innerHTML = '';
      const img = new Image();
      img.src = url;
      img.style.maxWidth = '100%'; img.style.maxHeight = '240px';
      resizedPreview.appendChild(img);
      downloadBtn.disabled = false;
      actionStatus.textContent = `Compressed to ${(bestResult.blob.size/1024).toFixed(1)} KB (q=${bestResult.quality.toFixed(2)}, ${bestResult.w}×${bestResult.h}).`;
    } else {
      actionStatus.textContent = `Could not reach ${(targetBytes/1024).toFixed(0)} KB even at 1×1 px.`;
    }
    compressBtn.disabled = false;
  });

  // ---- Download ----
  downloadBtn.addEventListener('click', () => {
    if (!resizedBlob) { actionStatus.textContent = 'Nothing to download.'; return; }
    const url = URL.createObjectURL(resizedBlob);
    const a = document.createElement('a');
    a.href = url;
    const ext = formatSelect.value === 'image/jpeg' ? 'jpg' : (formatSelect.value === 'image/webp' ? 'webp' : 'png');
    a.download = `resized.${ext}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    actionStatus.textContent = 'Download started.';
  });

  formatSelect.addEventListener('change', updateQualityUI);

  // Init
  updateQualityUI();
  updateModeUI();
})();
