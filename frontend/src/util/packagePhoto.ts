export function preparePackagePhoto(file: File): Promise<string> {
  if (!file.type.startsWith('image/')) return Promise.reject(new Error('Please choose an image'));
  return new Promise((resolve, reject) => {
    const image = new Image();
    const url = URL.createObjectURL(file);
    image.onload = () => {
      URL.revokeObjectURL(url);
      const scale = Math.min(1, 1024 / Math.max(image.width, image.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(image.width * scale));
      canvas.height = Math.max(1, Math.round(image.height * scale));
      const context = canvas.getContext('2d');
      if (!context) { reject(new Error('Could not prepare image')); return; }
      context.drawImage(image, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL('image/jpeg', 0.8);
      if (dataUrl.length > 3_000_000) { reject(new Error('Photo must be under about 2 MB')); return; }
      resolve(dataUrl);
    };
    image.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Could not read image')); };
    image.src = url;
  });
}
