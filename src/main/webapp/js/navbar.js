// js/navbar.js - v3
// Correctly handles both top-level dropdowns and nested flyout menus.

document.addEventListener('DOMContentLoaded', function() {
    // Select all items that can contain a dropdown, both top-level and nested
    const allDropdownTriggers = document.querySelectorAll('.main-navbar .dropdown, .main-navbar .sub-dropdown-trigger');

    allDropdownTriggers.forEach(trigger => {
        // --- Desktop Hover Logic ---
        trigger.addEventListener('mouseenter', function() {
            if (window.innerWidth > 992) {
                this.classList.add('is-open');
            }
        });

        trigger.addEventListener('mouseleave', function() {
            if (window.innerWidth > 992) {
                this.classList.remove('is-open');
            }
        });

        // --- Mobile/Tablet Click Logic ---
        const button = trigger.querySelector('.dropbtn, .sub-dropbtn');
        if (button) {
            button.addEventListener('click', function(event) {
                if (window.innerWidth <= 992) {
                    if (this.getAttribute('href') === 'javascript:void(0);') {
                        event.preventDefault();
                    }
                    event.stopPropagation();

                    const parentLi = this.parentElement;
                    const wasOpen = parentLi.classList.contains('is-open');

                    // Close all other dropdowns at the same level as the one being opened
                    const parentUl = parentLi.parentElement;
                    parentUl.querySelectorAll('.is-open').forEach(sibling => {
                        if (sibling !== parentLi) {
                           sibling.classList.remove('is-open');
                        }
                    });
                    
                    // Toggle the current dropdown
                    if (!wasOpen) {
                        parentLi.classList.add('is-open');
                    } else {
                        parentLi.classList.remove('is-open');
                    }
                }
            });
        }
    });

    // --- Global Click Listener to Close All Menus ---
    document.addEventListener('click', function(event) {
        if (!event.target.closest('.main-navbar')) {
            document.querySelectorAll('.main-navbar .is-open').forEach(openDropdown => {
                openDropdown.classList.remove('is-open');
            });
        }
    });

});