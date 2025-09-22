// js/navbar.js - v8 (Click-Only Navigation)
document.addEventListener('DOMContentLoaded', function() {
    
    const mainNavbar = document.querySelector('.main-navbar');
    
    function adjustBodyPadding() {
        if (mainNavbar) {
            const navbarHeight = mainNavbar.offsetHeight;
            document.body.style.paddingTop = navbarHeight + 'px';
        }
    }
    
    adjustBodyPadding();
    window.addEventListener('resize', adjustBodyPadding);

    const allDropdownTriggers = document.querySelectorAll('.main-navbar .dropdown, .main-navbar .sub-dropdown-trigger');

    allDropdownTriggers.forEach(trigger => {
        const button = trigger.querySelector('.dropbtn, .sub-dropbtn');
        if (button) {
            // Click handler for mobile (hamburger menu)
            button.addEventListener('click', function(event) {
                if (window.innerWidth <= 992) {
                    if (this.getAttribute('href') === 'javascript:void(0);') {
                        event.preventDefault();
                    }
                    event.stopPropagation();

                    const parentLi = this.parentElement;
                    const parentUl = parentLi.parentElement;
                    parentUl.querySelectorAll(':scope > .is-open').forEach(sibling => {
                        if (sibling !== parentLi) {
                           sibling.classList.remove('is-open');
                        }
                    });
                    
                    parentLi.classList.toggle('is-open');
                }
            });
            
            // Hover handlers for desktop
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
        }
    });

    // Global click listener to close ALL open menus when clicking outside
    document.addEventListener('click', function(event) {
        if (!event.target.closest('.main-navbar')) {
            document.querySelectorAll('.main-navbar .is-open').forEach(openDropdown => {
                openDropdown.classList.remove('is-open');
            });
        }
    });
	
	// Hamburger Menu Logic
	const mobileMenuToggle = document.getElementById('mobileMenuToggle');
	const navbarLinks = document.querySelector('.navbar-links');

	if (mobileMenuToggle && navbarLinks) {
	    mobileMenuToggle.addEventListener('click', function(e) {
	        e.stopPropagation();
	        navbarLinks.classList.toggle('mobile-open');
	        document.body.classList.toggle('menu-is-open');
	    });

	    document.addEventListener('click', function(event) {
	        if (!event.target.closest('.main-navbar') && navbarLinks.classList.contains('mobile-open')) {
	            navbarLinks.classList.remove('mobile-open');
	            document.body.classList.remove('menu-is-open');
	        }
	    });

	    window.addEventListener('resize', function() {
	        if (window.innerWidth > 992) {
	            navbarLinks.classList.remove('mobile-open');
	            document.body.classList.remove('menu-is-open');
	        }
	    });
	}
});