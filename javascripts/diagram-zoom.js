// Zoom, pan and fullscreen for Mermaid diagrams.
// Material renders each diagram into a closed shadow root and the SVG always fits its width, so
// CSS `zoom` has no effect. We scale the host with a transform and size a "sizer" box to the
// scaled dimensions, so the scroll area matches what is drawn.
document.addEventListener('DOMContentLoaded', function () {
  var MIN = 0.5;
  var MAX = 4;
  var STEP = 1.25;

  document.querySelectorAll('.mermaid').forEach(function (el) {
    if (el.closest('.diagram-zoom')) return;

    var box = document.createElement('div');
    box.className = 'diagram-zoom';
    var view = document.createElement('div');
    view.className = 'diagram-zoom__view';
    var sizer = document.createElement('div');
    var stage = document.createElement('div');
    stage.className = 'diagram-zoom__stage';

    el.parentNode.insertBefore(box, el);
    stage.appendChild(el); // Material later replaces <pre class="mermaid"> in place, inside the stage
    sizer.appendChild(stage);
    view.appendChild(sizer);

    var scale = 1;
    var bar = document.createElement('div');
    bar.className = 'diagram-zoom__bar';
    var label = document.createElement('span');
    label.className = 'diagram-zoom__label';

    function button(text, title, onClick) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'diagram-zoom__btn';
      b.textContent = text;
      b.title = title;
      b.setAttribute('aria-label', title);
      b.addEventListener('click', onClick);
      return b;
    }

    function apply(next) {
      scale = Math.min(MAX, Math.max(MIN, next));
      if (scale === 1) {
        // natural layout: the diagram fits the page width again
        stage.style.width = '';
        stage.style.transform = '';
        sizer.style.width = '';
        sizer.style.height = '';
      } else {
        // freeze the unscaled width once, then scale; offset sizes ignore transforms
        if (!stage.style.width) stage.style.width = stage.offsetWidth + 'px';
        var w = parseFloat(stage.style.width);
        var h = stage.offsetHeight;
        stage.style.transform = 'scale(' + scale + ')';
        sizer.style.width = w * scale + 'px';
        sizer.style.height = h * scale + 'px';
      }
      label.textContent = Math.round(scale * 100) + '%';
    }

    // entering or leaving full screen changes the available width
    box.addEventListener('fullscreenchange', function () { apply(1); });

    bar.appendChild(button('−', 'Zoom out', function () { apply(scale / STEP); }));
    bar.appendChild(label);
    bar.appendChild(button('+', 'Zoom in', function () { apply(scale * STEP); }));
    bar.appendChild(button('Reset', 'Reset zoom', function () { apply(1); view.scrollTo(0, 0); }));
    if (box.requestFullscreen) {
      bar.appendChild(button('⛶', 'Full screen', function () {
        if (document.fullscreenElement === box) document.exitFullscreen();
        else box.requestFullscreen();
      }));
    }

    box.appendChild(bar);
    box.appendChild(view);
    apply(1);

    // Ctrl/⌘ + wheel zooms; a plain wheel keeps scrolling the page
    view.addEventListener('wheel', function (e) {
      if (!e.ctrlKey && !e.metaKey) return;
      e.preventDefault();
      apply(e.deltaY < 0 ? scale * STEP : scale / STEP);
    }, { passive: false });

    // Drag to pan when the diagram is larger than the view
    var drag = null;
    view.addEventListener('pointerdown', function (e) {
      if (e.button !== 0) return;
      drag = { x: e.clientX, y: e.clientY, left: view.scrollLeft, top: view.scrollTop };
      view.classList.add('is-dragging');
    });
    window.addEventListener('pointermove', function (e) {
      if (!drag) return;
      view.scrollLeft = drag.left - (e.clientX - drag.x);
      view.scrollTop = drag.top - (e.clientY - drag.y);
    });
    window.addEventListener('pointerup', function () {
      drag = null;
      view.classList.remove('is-dragging');
    });
  });
});
