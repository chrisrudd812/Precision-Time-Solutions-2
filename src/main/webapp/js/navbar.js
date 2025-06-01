// js/navbar.js - v28.05.24 (Accrual Modal Logic Removed)

document.addEventListener('DOMContentLoaded', function() {
    console.log("navbar.js file loaded START (Accrual Modal Logic from navbar.jspf removed)");

    // --- General Navbar Interactivity (e.g., dropdowns, mobile menu toggle) ---
    const dropdowns = document.querySelectorAll('.main-navbar .dropdown');
    dropdowns.forEach(dropdown => {
        const button = dropdown.querySelector('.dropbtn');
        const content = dropdown.querySelector('.dropdown-content');
        if (button && content) {
            // Desktop hover
            dropdown.addEventListener('mouseenter', () => {
                if (window.innerWidth > 768) { // Example breakpoint for hover behavior
                    content.style.display = 'block';
                }
            });
            dropdown.addEventListener('mouseleave', () => {
                 if (window.innerWidth > 768) {
                    content.style.display = 'none';
                }
            });
            // Click for touch or smaller screens (can be combined with hover for robust behavior)
            button.addEventListener('click', (event) => {
                if (button.getAttribute('href') === 'javascript:void(0);') {
                    event.preventDefault();
                }
                let isVisible = content.style.display === 'block';
                // Hide other open main dropdowns before showing this one
                document.querySelectorAll('.main-navbar > .navbar-links-container > .navbar-links > li > .dropdown-content').forEach(dc => {
                    if (dc !== content) dc.style.display = 'none';
                });
                content.style.display = isVisible ? 'none' : 'block';
                event.stopPropagation(); 
            });
        }
        
        // Handle sub-dropdowns
        const subDropdowns = dropdown.querySelectorAll('.sub-dropdown-trigger');
        subDropdowns.forEach(sub => {
            const subBtn = sub.querySelector('.sub-dropbtn');
            const subContent = sub.querySelector('.sub-dropdown-content');
            if (subBtn && subContent) {
                 sub.addEventListener('mouseenter', () => {
                    if (window.innerWidth > 768) {
                        subContent.style.display = 'block';
                    }
                });
                sub.addEventListener('mouseleave', () => {
                    if (window.innerWidth > 768) {
                       subContent.style.display = 'none';
                    }
                });
                subBtn.addEventListener('click', (event) => {
                     if (subBtn.getAttribute('href') === 'javascript:void(0);') {
                        event.preventDefault();
                    }
                    let isSubVisible = subContent.style.display === 'block';
                    // Hide other open sub-dropdowns within the SAME parent dropdown
                    sub.closest('.dropdown-content').querySelectorAll('.sub-dropdown-content').forEach(sdc => {
                        if (sdc !== subContent) sdc.style.display = 'none';
                    });
                    subContent.style.display = isSubVisible ? 'none' : 'block';
                    event.stopPropagation();
                });
            }
        });
    });

    // Hide all dropdowns if clicked outside
    document.addEventListener('click', function (event) {
        let clickedInsideNavbar = event.target.closest('.main-navbar');
        if (!clickedInsideNavbar) {
            document.querySelectorAll('.main-navbar .dropdown-content').forEach(content => {
                content.style.display = 'none';
            });
        }
    });
    // --- End General Navbar Interactivity ---

    console.log("NAVBAR.JS: Old accrual modal specific logic has been removed as the modal is no longer part of navbar.jspf.");
    console.log("--- Navbar.js DOMContentLoaded END ---");
});